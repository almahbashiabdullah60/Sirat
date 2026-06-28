package com.atyafcode.sirat.services.vpn

import android.util.LruCache
import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.FilterDao
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DnsResolver(
    private val filterRepository: FilterRepository,
    private val filterDao: FilterDao,
    private val dnsServer: InetAddress = InetAddress.getByName("1.1.1.1"),
    private val protectSocket: ((DatagramSocket) -> Boolean)? = null
) {
    private val safeSearchMap = mutableMapOf<String, InetAddress>()

    // نطاق عمل خفيف لتسجيل النطاقات المحظورة دون حجز مسار الحزمة (تجنّب runBlocking).
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // كاش لاستجابات الخادم العلوي مفاتيحه "domain:type" لإلغاء تكرار الاستعلامات الشائعة.
    private val upstreamCache = LruCache<String, ByteArray>(256)
    private val forwardLock = Any()
    private var forwardSocket: DatagramSocket? = null

    init {
        // SafeSearch resolution is now handled by the caller or lazily if needed.
        // For now, keeping it simple but avoiding blocking runBlocking in init if possible.
    }

    suspend fun initSafeSearch() = withContext(Dispatchers.IO) {
        resolveSafeSearchIps()
    }

    private fun resolveSafeSearchIps() {
        val targets = mapOf(
            "forcesafesearch.google.com" to setOf("google.com", "www.google.com"),
            "strict.bing.com" to setOf("bing.com", "www.bing.com"),
            "restrict.youtube.com" to setOf("youtube.com", "www.youtube.com", "m.youtube.com"),
            "safe.duckduckgo.com" to setOf("duckduckgo.com", "www.duckduckgo.com")
        )
        for ((safeDomain, originalDomains) in targets) {
            try {
                val ip = InetAddress.getByName(safeDomain)
                for (original in originalDomains) {
                    safeSearchMap[original] = ip
                }
            } catch (_: Exception) {
            }
        }
    }

    fun close() {
        synchronized(forwardLock) {
            forwardSocket?.close()
            forwardSocket = null
        }
        upstreamCache.evictAll()
        logScope.cancel()
    }

    suspend fun resolve(dnsPayloadBytes: ByteArray, onResponse: (ByteArray) -> Unit) {
        val query = try {
            Message(dnsPayloadBytes)
        } catch (_: Exception) {
            return
        }

        val question = query.question ?: return
        val domain = question.name.toString(true).lowercase()
        val typeKey = "$domain:${question.type}"

        // محاولة إرجاع استجابة مخزّنة مسبقاً (تُوفّر استعلامات علوية و CPU).
        upstreamCache.get(typeKey)?.let { cached ->
            // طابق مُعرّف المعاملة (TXID) للاستعلام الحالي عبر أول بايتين فقط.
            if (cached.size >= 2) {
                val reply = cached.copyOf()
                reply[0] = dnsPayloadBytes[0]
                reply[1] = dnsPayloadBytes[1]
                onResponse(reply)
                return
            }
        }

        val safeSearch = DnsFilterController.safeSearch
        if (safeSearch && question.type == Type.A) {
            // Google specific logic: Only redirect main search domains, NOT services like mail, drive, docs
            val isGoogleSearch = domain == "google.com" || domain == "www.google.com" || 
                                (domain.startsWith("google.") && domain.count { it == '.' } <= 2)
            
            val targetDomain = when {
                isGoogleSearch -> "google.com"
                domain == "bing.com" || domain == "www.bing.com" -> "bing.com"
                domain == "youtube.com" || domain == "www.youtube.com" || domain == "m.youtube.com" -> "youtube.com"
                domain == "duckduckgo.com" || domain == "www.duckduckgo.com" -> "duckduckgo.com"
                else -> null
            }

            val safeIp = targetDomain?.let { safeSearchMap[it] }

            if (safeIp != null) {
                val response = Message(query.toWire().size)
                response.header.setFlag(Flags.QR.toInt())
                response.header.setFlag(Flags.AA.toInt())
                response.addRecord(query.getQuestion(), Section.QUESTION)
                response.addRecord(ARecord(question.name, Type.A, 300, safeIp), Section.ANSWER)
                onResponse(response.toWire())
                return
            }
        }

        if (filterRepository.shouldBlockDomain(
                domain,
                DnsFilterController.blockPorn,
                DnsFilterController.blockGambling,
                DnsFilterController.blockSocial,
                DnsFilterController.keywords.isNotEmpty()
            )
        ) {
            val response = Message(query.toWire().size)
            response.header.setFlag(Flags.QR.toInt())
            response.header.setFlag(Flags.AA.toInt())
            response.header.setRcode(Rcode.NXDOMAIN.toInt())
            response.addRecord(query.getQuestion(), Section.QUESTION)
            onResponse(response.toWire())
            logBlockedDomain(domain, "blocklist")
        } else {
            forwardQuery(dnsPayloadBytes, onResponse, typeKey)
        }
    }

    private fun forwardQuery(dnsPayloadBytes: ByteArray, onResponse: (ByteArray) -> Unit, typeKey: String) {
        val maxRetries = 2
        for (attempt in 1..maxRetries) {
            try {
                val reply = synchronized(forwardLock) {
                    val socket = getForwardSocket()
                    val packet = DatagramPacket(dnsPayloadBytes, dnsPayloadBytes.size, dnsServer, 53)
                    socket.send(packet)

                    val buffer = ByteArray(4096)
                    val replyPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(replyPacket)
                    replyPacket.data.copyOf(replyPacket.length)
                }
                // خزّن الاستجابة للتكرارات القادمة (مع تطبيع TXID إلى الصفر في النسخة المخزّنة).
                if (reply.size >= 2) {
                    val cached = reply.copyOf()
                    cached[0] = 0
                    cached[1] = 0
                    upstreamCache.put(typeKey, cached)
                }
                onResponse(reply)
                return
            } catch (_: SocketTimeoutException) {
                // أعِد المحاولة إذا كان attempt < maxRetries
            } catch (_: Exception) {
                return
            }
        }
        try {
            val query = Message(dnsPayloadBytes)
            val response = Message(query.toWire().size)
            response.header.setFlag(Flags.QR.toInt())
            response.header.setRcode(Rcode.SERVFAIL.toInt())
            response.addRecord(query.getQuestion(), Section.QUESTION)
            onResponse(response.toWire())
        } catch (_: Exception) { }
    }

    private fun getForwardSocket(): DatagramSocket {
        forwardSocket?.let { if (!it.isClosed) return it }
        val socket = DatagramSocket()
        socket.soTimeout = 2000
        protectSocket?.invoke(socket)
        forwardSocket = socket
        return socket
    }

    private fun logBlockedDomain(domain: String, reason: String) {
        // تسجيل غير متزامن (fire-and-forget) حتى لا يحجز مسار معالجة الحزمة.
        try {
            logScope.launch {
                filterDao.insertLog(BlockedLog(domain = domain, reason = reason))
            }
        } catch (_: Exception) {
        }
    }
}

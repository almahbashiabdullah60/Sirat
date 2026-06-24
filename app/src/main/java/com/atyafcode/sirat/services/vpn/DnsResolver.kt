package com.atyafcode.sirat.services.vpn

import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.FilterDao
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.Dispatchers
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
    private var dnsSocket: DatagramSocket? = null

    init {
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            resolveSafeSearchIps()
        }
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

    private fun getSocket(): DatagramSocket {
        return dnsSocket ?: synchronized(this) {
            dnsSocket ?: DatagramSocket().also {
                it.soTimeout = 2000
                dnsSocket = it
            }
        }
    }

    fun close() {
        dnsSocket?.close()
        dnsSocket = null
    }

    suspend fun resolve(dnsPayloadBytes: ByteArray, onResponse: (ByteArray) -> Unit) {
        val query = try {
            Message(dnsPayloadBytes)
        } catch (_: Exception) {
            return
        }

        val question = query.question ?: return
        val domain = question.name.toString(true).lowercase()

        val safeSearch = DnsFilterController.safeSearch
        if (safeSearch && question.type == Type.A) {
            val safeIp = safeSearchMap.entries.firstOrNull { (originalName) ->
                domain == originalName || domain.endsWith(".$originalName")
            }?.value
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
            forwardQuery(dnsPayloadBytes, onResponse)
        }
    }

    private fun forwardQuery(dnsPayloadBytes: ByteArray, onResponse: (ByteArray) -> Unit) {
        val maxRetries = 2
        for (attempt in 1..maxRetries) {
            try {
                var socket = dnsSocket
                if (socket == null || socket.isClosed) {
                    socket = DatagramSocket()
                    socket.soTimeout = 2000
                    protectSocket?.invoke(socket)
                    dnsSocket = socket
                }
                val packet = DatagramPacket(dnsPayloadBytes, dnsPayloadBytes.size, dnsServer, 53)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)

                onResponse(reply.data.copyOf(reply.length))
                return
            } catch (_: SocketTimeoutException) {
                if (attempt < maxRetries) {
                    dnsSocket?.close()
                    dnsSocket = null
                }
            } catch (_: Exception) {
                dnsSocket?.close()
                dnsSocket = null
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

    private fun logBlockedDomain(domain: String, reason: String) {
        try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                filterDao.insertLog(BlockedLog(domain = domain, reason = reason))
            }
        } catch (_: Exception) {
        }
    }
}

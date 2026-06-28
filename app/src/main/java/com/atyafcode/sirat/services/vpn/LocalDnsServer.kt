package com.atyafcode.sirat.services.vpn

import android.util.Log
import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.FilterDao
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xbill.DNS.ARecord
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext

class LocalDnsServer(
    private val filterRepository: FilterRepository,
    private val filterDao: FilterDao,
    private val localPort: Int = 5354,
    private val upstreamDns: InetAddress = InetAddress.getByName("1.1.1.1"),
    private val blockPorn: Boolean = true,
    private val blockGambling: Boolean = true,
    private val blockSocial: Boolean = false,
    private val useKeywords: Boolean = true,
    private val safeSearchEnabled: Boolean = true
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private var serverSocket: DatagramSocket? = null
    private var serverJob: Job? = null

    private val safeSearchMap = mutableMapOf<String, InetAddress>()
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val forwardLock = Any()
    private var forwardSocket: DatagramSocket? = null

    init {
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
            } catch (_: Exception) { }
        }
    }

    fun start() {
        if (serverJob?.isActive == true) return
        Log.d("LocalDnsServer", "Starting DNS proxy on port $localPort")
        serverJob = launch {
            try {
                val socket = DatagramSocket(localPort)
                serverSocket = socket
                Log.d("LocalDnsServer", "Listening on port $localPort")
                val buffer = ByteArray(4096)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    launch {
                        handleQuery(packet.data.copyOf(packet.length), packet.address, packet.port, socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalDnsServer", "Server error", e)
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        synchronized(forwardLock) {
            forwardSocket?.close()
            forwardSocket = null
        }
        logScope.cancel()
        job.cancel()
    }

    private fun handleQuery(dnsPayload: ByteArray, srcAddr: InetAddress, srcPort: Int, socket: DatagramSocket) {
        val query = try {
            Message(dnsPayload)
        } catch (_: Exception) { return }

        val question = query.question ?: return
        val domain = question.name.toString(true).lowercase()
        val response: ByteArray

        if (safeSearchEnabled && question.type == Type.A) {
            val safeIp = safeSearchMap.entries.firstOrNull { (originalName) ->
                domain == originalName || domain.endsWith(".$originalName")
            }?.value
            if (safeIp != null) {
                val msg = Message(query.toWire().size)
                msg.header.setFlag(Flags.QR.toInt())
                msg.header.setFlag(Flags.AA.toInt())
                msg.addRecord(query.getQuestion(), Section.QUESTION)
                msg.addRecord(ARecord(question.name, Type.A, 300, safeIp), Section.ANSWER)
                response = msg.toWire()
                sendResponse(socket, response, srcAddr, srcPort)
                return
            }
        }

        if (filterRepository.shouldBlockDomain(domain, blockPorn, blockGambling, blockSocial, useKeywords)) {
            val msg = Message(query.toWire().size)
            msg.header.setFlag(Flags.QR.toInt())
            msg.header.setFlag(Flags.AA.toInt())
            msg.header.setRcode(Rcode.NXDOMAIN.toInt())
            msg.addRecord(query.getQuestion(), Section.QUESTION)
            response = msg.toWire()
            sendResponse(socket, response, srcAddr, srcPort)
            logBlockedDomain(domain, "blocklist")
        } else {
            forwardQuery(dnsPayload, socket, srcAddr, srcPort)
        }
    }

    private fun forwardQuery(dnsPayload: ByteArray, socket: DatagramSocket, srcAddr: InetAddress, srcPort: Int) {
        try {
            val reply = synchronized(forwardLock) {
                val fs = getForwardSocket()
                val request = DatagramPacket(dnsPayload, dnsPayload.size, upstreamDns, 53)
                fs.send(request)
                val buffer = ByteArray(4096)
                val replyPacket = DatagramPacket(buffer, buffer.size)
                fs.receive(replyPacket)
                replyPacket.data.copyOf(replyPacket.length)
            }
            sendResponse(socket, reply, srcAddr, srcPort)
        } catch (_: Exception) { }
    }

    private fun getForwardSocket(): DatagramSocket {
        forwardSocket?.let { if (!it.isClosed) return it }
        val socket = DatagramSocket()
        socket.soTimeout = 2000
        forwardSocket = socket
        return socket
    }

    private fun sendResponse(socket: DatagramSocket, data: ByteArray, addr: InetAddress, port: Int) {
        try {
            socket.send(DatagramPacket(data, data.size, addr, port))
        } catch (_: Exception) { }
    }

    private fun logBlockedDomain(domain: String, reason: String) {
        try {
            logScope.launch {
                filterDao.insertLog(BlockedLog(domain = domain, reason = reason))
            }
        } catch (_: Exception) { }
    }
}

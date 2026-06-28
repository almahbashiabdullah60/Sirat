package com.atyafcode.sirat.services.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.atyafcode.sirat.MainActivity
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.LocaleUtils
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SiratVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var resolver: DnsResolver? = null
    private val outputLock = ReentrantLock()

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification())

        val db = FilterDatabase.getInstance(this@SiratVpnService.applicationContext as android.app.Application)
        val filterRepository = FilterRepository.getInstance(db)
        filterRepository.setKeywords(DnsFilterController.keywords)
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            filterRepository.loadCaches()
        }
        resolver = DnsResolver(
            filterRepository = filterRepository,
            filterDao = db.filterDao(),
            protectSocket = { socket -> this.protect(socket) }
        ).apply {
            serviceScope.launch { initSafeSearch() }
        }

        Log.d(TAG, "Caches loaded: ${filterRepository.cacheStats()}")

        val builder = Builder()
            .setSession(getString(R.string.vpn_filter_session_name))
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS_SERVER)
            .addRoute(VPN_DNS_SERVER, 32)

        vpnInterface = builder.establish() ?: run {
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        vpnJob = serviceScope.launch {
            val fd = vpnInterface!!.fileDescriptor
            FileInputStream(fd).use { input ->
                FileOutputStream(fd).use { output ->
                    // معالجة الحزم تسلسلياً: حركة DNS منخفضة الحجم، وتجنّب إنشاء coroutine
                    // لكل حزمة يقلّل ضغط الـ GC والـ thread pool ويخفض استهلاك البطارية.
                    val buffer = ByteArray(32767)
                    while (isActive) {
                        val length = input.read(buffer)
                        if (length > 0) {
                            processPacket(buffer, length, output)
                        }
                    }
                }
            }
        }
    }

    private suspend fun processPacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 20) {
            safeWrite(output, buffer, 0, length)
            return
        }
        if ((buffer[0].toInt() shr 4) != 4) {
            safeWrite(output, buffer, 0, length)
            return
        }
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength + 8 > length || buffer[9].toInt() != 17) {
            safeWrite(output, buffer, 0, length)
            return
        }
        val destPort = ((buffer[headerLength + 2].toInt() and 0xFF) shl 8) or
                (buffer[headerLength + 3].toInt() and 0xFF)
        if (destPort != 53) {
            safeWrite(output, buffer, 0, length)
            return
        }
        val udpLength = ((buffer[headerLength + 4].toInt() and 0xFF) shl 8) or
                (buffer[headerLength + 5].toInt() and 0xFF)
        val dnsStart = headerLength + 8
        if (dnsStart > length || headerLength + udpLength > length) {
            safeWrite(output, buffer, 0, length)
            return
        }
        val dnsPayload = buffer.copyOfRange(dnsStart, headerLength + udpLength)

        val resolver = resolver ?: run {
            safeWrite(output, buffer, 0, length)
            return
        }
        resolver.resolve(dnsPayload) { responseBytes ->
            writeResponse(buffer, length, headerLength, responseBytes, output)
        }
    }

    private fun safeWrite(output: FileOutputStream, buffer: ByteArray, offset: Int, length: Int) {
        try {
            outputLock.withLock {
                output.write(buffer, offset, length)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to VPN interface", e)
        }
    }

    private fun writeResponse(
        original: ByteArray, originalLength: Int, ipHeaderLen: Int,
        dnsResponse: ByteArray, output: FileOutputStream
    ) {
        val totalLen = ipHeaderLen + 8 + dnsResponse.size
        val response = ByteArray(totalLen)
        
        // Copy IP Header and UDP Header (ports only initially)
        System.arraycopy(original, 0, response, 0, ipHeaderLen + 8)
        System.arraycopy(dnsResponse, 0, response, ipHeaderLen + 8, dnsResponse.size)

        // Update IP Total Length
        response[2] = ((totalLen shr 8) and 0xFF).toByte()
        response[3] = (totalLen and 0xFF).toByte()

        // Swap IP addresses
        val srcIp = original.copyOfRange(12, 16)
        val dstIp = original.copyOfRange(16, 20)
        System.arraycopy(dstIp, 0, response, 12, 4)
        System.arraycopy(srcIp, 0, response, 16, 4)

        // Reset IP Checksum for calculation
        response[10] = 0
        response[11] = 0
        val ipChecksum = IpPacketUtils.calculateChecksum(response, 0, ipHeaderLen)
        response[10] = ((ipChecksum.toInt() shr 8) and 0xFF).toByte()
        response[11] = (ipChecksum.toInt() and 0xFF).toByte()

        // Update UDP Header
        // Swap Ports
        response[ipHeaderLen + 0] = original[ipHeaderLen + 2]
        response[ipHeaderLen + 1] = original[ipHeaderLen + 3]
        response[ipHeaderLen + 2] = original[ipHeaderLen + 0]
        response[ipHeaderLen + 3] = original[ipHeaderLen + 1]

        // Update UDP Length
        val udpLen = 8 + dnsResponse.size
        response[ipHeaderLen + 4] = ((udpLen shr 8) and 0xFF).toByte()
        response[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()

        // UDP Checksum (set to 0, which is optional for IPv4 UDP)
        response[ipHeaderLen + 6] = 0
        response[ipHeaderLen + 7] = 0

        safeWrite(output, response, 0, totalLen)
    }

    private fun stopVpn() {
        isRunning = false
        vpnJob?.cancel()
        vpnJob = null
        resolver?.close()
        resolver = null
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopVpn()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_filter_notification_title))
            .setContentText(getString(R.string.vpn_filter_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_filter_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_filter_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.atyafcode.sirat.vpn.START"
        const val ACTION_STOP = "com.atyafcode.sirat.vpn.STOP"
        const val CHANNEL_ID = "vpn_filter_channel"
        private const val NOTIFICATION_ID = 113
        private const val VPN_ADDRESS = "10.8.0.2"
        private const val VPN_DNS_SERVER = "10.8.0.1"

        @Volatile
        var isRunning = false
        private const val TAG = "SiratVpnService"
    }
}

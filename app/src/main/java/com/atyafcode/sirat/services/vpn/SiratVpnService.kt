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
import androidx.core.app.NotificationCompat
import com.atyafcode.sirat.MainActivity
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.LocaleUtils
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.filter.SyncManager
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

class SiratVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var resolver: DnsResolver? = null

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

        val db = FilterDatabase.getInstance(this@SiratVpnService.applicationContext as android.app.Application)
        val filterRepository = FilterRepository(db)
        resolver = DnsResolver(
            filterRepository = filterRepository,
            filterDao = db.filterDao()
        )

        serviceScope.launch {
            SyncManager(this@SiratVpnService, db, filterRepository).syncAll()
        }

        val builder = Builder()
            .setSession(getString(R.string.vpn_filter_session_name))
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS_SERVER)
            .addRoute(VPN_DNS_SERVER, 32)

        vpnInterface = builder.establish() ?: run {
            isRunning = false
            return
        }

        startForeground(NOTIFICATION_ID, createNotification())

        vpnJob = serviceScope.launch {
            val fd = vpnInterface!!.fileDescriptor
            FileInputStream(fd).use { input ->
                FileOutputStream(fd).use { output ->
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

    private fun processPacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 20) {
            output.write(buffer, 0, length)
            return
        }
        if ((buffer[0].toInt() shr 4) != 4) {
            output.write(buffer, 0, length)
            return
        }
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength + 2 > length || buffer[9].toInt() != 17) {
            output.write(buffer, 0, length)
            return
        }
        val destPort = ((buffer[headerLength + 2].toInt() and 0xFF) shl 8) or
                (buffer[headerLength + 3].toInt() and 0xFF)
        if (destPort != 53) {
            output.write(buffer, 0, length)
            return
        }
        val udpLength = ((buffer[headerLength + 4].toInt() and 0xFF) shl 8) or
                (buffer[headerLength + 5].toInt() and 0xFF)
        val dnsStart = headerLength + 8
        if (dnsStart + 12 > length) {
            output.write(buffer, 0, length)
            return
        }
        val dnsPayload = buffer.copyOfRange(dnsStart, headerLength + udpLength)

        val resolver = resolver ?: run {
            output.write(buffer, 0, length)
            return
        }
        kotlinx.coroutines.runBlocking {
            resolver.resolve(dnsPayload) { responseBytes ->
                writeResponse(buffer, length, headerLength, responseBytes, output)
            }
        }
    }

    private fun writeResponse(
        original: ByteArray, originalLength: Int, ipHeaderLen: Int,
        dnsResponse: ByteArray, output: FileOutputStream
    ) {
        val totalLen = ipHeaderLen + 8 + dnsResponse.size
        val response = ByteArray(totalLen)
        System.arraycopy(original, 0, response, 0, ipHeaderLen + 8)
        System.arraycopy(dnsResponse, 0, response, ipHeaderLen + 8, dnsResponse.size)
        response[2] = ((totalLen shr 8) and 0xFF).toByte()
        response[3] = (totalLen and 0xFF).toByte()
        val srcIp = original.copyOfRange(12, 16)
        val dstIp = original.copyOfRange(16, 20)
        System.arraycopy(dstIp, 0, response, 12, 4)
        System.arraycopy(srcIp, 0, response, 16, 4)
        response[ipHeaderLen + 2] = original[ipHeaderLen + 2]
        response[ipHeaderLen + 3] = original[ipHeaderLen + 3]
        output.write(response)
    }

    private fun stopVpn() {
        isRunning = false
        vpnJob?.cancel()
        vpnJob = null
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
    }
}

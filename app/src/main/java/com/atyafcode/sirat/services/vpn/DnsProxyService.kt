package com.atyafcode.sirat.services.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.atyafcode.sirat.MainActivity
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.LocaleUtils
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.filter.SyncManager
import com.atyafcode.sirat.data.repository.FilterRepository

class DnsProxyService : android.app.Service() {

    private var dnsServer: LocalDnsServer? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProxy()
            ACTION_STOP -> stopProxy()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    private fun startProxy() {
        if (dnsServer != null) return
        Log.d(TAG, "Starting DNS proxy service...")
        isRunning = true

        // MUST call startForeground IMMEDIATELY, before any heavy work
        startForeground(NOTIFICATION_ID, createNotification())

        val app = applicationContext as android.app.Application
        val db = FilterDatabase.getInstance(app)
        val filterRepository = FilterRepository(db)
        val syncManager = SyncManager(app, db, filterRepository)

        kotlinx.coroutines.runBlocking {
            Log.d(TAG, "Syncing domain lists...")
            syncManager.syncAll()
            Log.d(TAG, "Caches: ${filterRepository.cacheStats()}")
        }

        dnsServer = LocalDnsServer(
            filterRepository = filterRepository,
            filterDao = db.filterDao()
        ).also { it.start() }

        Log.d(TAG, "Applying iptables / redirect rules...")
        val status = IptablesManager.applyRules()
        lastStatus = status
        Log.d(TAG, "Redirect setup result:\n$status")

        Log.d(TAG, "DNS proxy service is running")
    }

    private fun stopProxy() {
        Log.d(TAG, "Stopping DNS proxy service...")
        val cleanup = IptablesManager.clearRules()
        Log.d(TAG, "iptables cleanup:\n$cleanup")
        dnsServer?.stop()
        dnsServer = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
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

    companion object {
        const val ACTION_START = "com.atyafcode.sirat.proxy.START"
        const val ACTION_STOP = "com.atyafcode.sirat.proxy.STOP"
        const val CHANNEL_ID = "vpn_filter_channel"

        @Volatile
        var isRunning = false

        @Volatile
        var lastStatus: String = ""

        private const val NOTIFICATION_ID = 114
        private const val TAG = "DnsProxyService"
    }
}

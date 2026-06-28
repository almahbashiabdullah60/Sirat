package com.atyafcode.sirat.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atyafcode.sirat.MainActivity
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.core.utils.hasUsagePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastPackageName: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.atyafcode.sirat.core.utils.LocaleUtils.applyLocale(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (hasUsagePermission()) {
                        val currentPackage = AppLockManager.getForegroundPackage(this@UsageLockService)
                        if (currentPackage != null && currentPackage != lastPackageName) {
                            lastPackageName = currentPackage
                            AppLockManager.handlePackageChange(this@UsageLockService, currentPackage)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UsageLockService", "Error in monitoring loop: ${e.message}")
                }
                delay(500)
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.welc_sirat)) // Updated to use localized welcome text
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
                "AppLock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "usage_lock_service_channel"
    }
}



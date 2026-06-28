package com.atyafcode.sirat.services

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.features.lockscreen.ui.PasswordOverlayActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AppLockManager {
    private const val TAG = "AppLockManager"

    enum class BiometricState {
        IDLE, AUTH_STARTED
    }

    var currentBiometricState = BiometricState.IDLE
    var temporarilyUnlockedApp: String = ""
    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    val isLockScreenShown = AtomicBoolean(false)

    private var recentlyLeftApp: String = ""
    private var recentlyLeftTime: Long = 0L
    private const val GRACE_PERIOD_MS = 300L

    fun setRecentlyLeftApp(packageName: String) {
        recentlyLeftApp = packageName
        recentlyLeftTime = System.currentTimeMillis()
    }

    fun checkAndRestoreRecentlyLeftApp(packageName: String): Boolean {
        if (packageName == recentlyLeftApp && packageName.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - recentlyLeftTime
            if (elapsed <= GRACE_PERIOD_MS) {
                temporarilyUnlockedApp = packageName
                recentlyLeftApp = ""
                recentlyLeftTime = 0L
                return true
            }
        }
        return false
    }

    private val ALL_APP_LOCK_SERVICES = setOf(
        ShizukuAppLockService::class.java,
        UsageLockService::class.java
    )

    fun unlockApp(packageName: String) {
        temporarilyUnlockedApp = packageName
        appUnlockTimes[packageName] = System.currentTimeMillis()
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        unlockApp(packageName)
    }

    fun reportBiometricAuthStarted() {}
    fun reportBiometricAuthFinished() {}

    fun isAppTemporarilyUnlocked(packageName: String): Boolean =
        temporarilyUnlockedApp == packageName

    fun clearTemporarilyUnlockedApp() {
        temporarilyUnlockedApp = ""
    }

    fun clearAppUnlockState(packageName: String) {
        if (temporarilyUnlockedApp == packageName) {
            temporarilyUnlockedApp = ""
        }
        appUnlockTimes.remove(packageName)
    }

    fun stopAllOtherServices(context: Context, excludeService: Class<*>) {
        ALL_APP_LOCK_SERVICES
            .filter { it != excludeService }
            .forEach { context.stopService(Intent(context, it)) }
    }

    fun getForegroundPackage(context: Context): String? {
        return try {
            val usageStatsManager = context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
            if (stats != null && stats.isNotEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                return sortedStats[0].packageName
            }
            null
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error getting foreground package: ${e.message}")
            null
        }
    }

    fun handlePackageChange(context: Context, packageName: String) {
        val repository = context.appLockRepository()
        if (repository.isAppLocked(packageName)) {
            if (!isAppTemporarilyUnlocked(packageName)) {
                val intent = Intent(context, PasswordOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("locked_package", packageName)
                }
                context.startActivity(intent)
            }
        }
    }
}

fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isDeviceLocked
}


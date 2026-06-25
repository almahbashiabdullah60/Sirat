package com.atyafcode.sirat.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.core.utils.appLockRepository
import com.atyafcode.sirat.data.repository.BackendImplementation
import com.atyafcode.sirat.services.AppLockAccessibilityService
import com.atyafcode.sirat.services.ShizukuAppLockService
import com.atyafcode.sirat.services.UsageLockService
import com.atyafcode.sirat.services.isServiceRunning

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = context.appLockRepository()
        
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App package replaced, clearing old logs")
                // Clear all old logs on app update
                LogUtils.clearAllLogs()
                try {
                    AppLockServiceStarter.startAppropriateServices(context, repository)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting services on package replace", e)
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_USER_PRESENT -> {
                try {
                    AppLockServiceStarter.startAppropriateServices(context, repository)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting services on boot or unlock", e)
                }
            }
            else -> {
                Log.w(TAG, "Invalid intent action: ${intent.action}")
            }
        }
    }

    private fun startAppropriateServices(
        context: Context,
        repository: com.atyafcode.sirat.data.repository.AppLockRepository
    ) {
        if (repository.isAntiUninstallEnabled()) {
            startService(context, AppLockAccessibilityService::class.java)
        }

        when (repository.getBackendImplementation()) {
            BackendImplementation.SHIZUKU -> {
                startService(context, ShizukuAppLockService::class.java)
            }

            BackendImplementation.ACCESSIBILITY -> {
                startService(context, AppLockAccessibilityService::class.java)
            }

            BackendImplementation.USAGE_STATS -> {
                startService(context, UsageLockService::class.java)
            }
        }
    }

    private fun startService(context: Context, serviceClass: Class<*>) {
        try {
            if (context.isServiceRunning(serviceClass)) {
                Log.d(TAG, "Service already running: ${serviceClass.simpleName}")
                return
            }

            val serviceIntent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                serviceClass != AppLockAccessibilityService::class.java
            ) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Started service: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}


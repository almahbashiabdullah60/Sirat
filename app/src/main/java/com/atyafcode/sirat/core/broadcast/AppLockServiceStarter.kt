package com.atyafcode.sirat.core.broadcast

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.atyafcode.sirat.data.repository.BackendImplementation
import com.atyafcode.sirat.services.AppLockAccessibilityService
import com.atyafcode.sirat.services.ShizukuAppLockService
import com.atyafcode.sirat.services.UsageLockService
import com.atyafcode.sirat.services.isServiceRunning

object AppLockServiceStarter {
    private const val TAG = "AppLockServiceStarter"

    fun startAppropriateServices(
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

}


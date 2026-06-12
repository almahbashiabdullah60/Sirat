package com.atyafcode.sirat.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.services.AppLockManager

class DeviceUnlockReceiver(private val onDeviceUnlocked: () -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_USER_PRESENT -> {
                LogUtils.d("DeviceUnlockReceiver", "Device unlocked (ACTION_USER_PRESENT)")
                onDeviceUnlocked()
            }

            Intent.ACTION_SCREEN_OFF -> {
                AppLockManager.clearTemporarilyUnlockedApp()
                AppLockManager.appUnlockTimes.clear()
                LogUtils.d("DeviceUnlockReceiver", "Screen turned OFF (locked)")
            }
        }
    }
}


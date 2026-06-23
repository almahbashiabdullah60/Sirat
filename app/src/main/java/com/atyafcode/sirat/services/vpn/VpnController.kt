package com.atyafcode.sirat.services.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService

object VpnController {

    fun isVpnRunning(): Boolean {
        return SiratVpnService.isRunning
    }

    fun getPrepareIntent(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    fun start(context: Context) {
        val intent = Intent(context, SiratVpnService::class.java).apply {
            action = SiratVpnService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, SiratVpnService::class.java).apply {
            action = SiratVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}

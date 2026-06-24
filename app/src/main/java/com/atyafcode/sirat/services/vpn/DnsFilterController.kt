package com.atyafcode.sirat.services.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService

object DnsFilterController {

    @Volatile
    var blockPorn = true
    @Volatile
    var blockGambling = true
    @Volatile
    var blockSocial = false
    @Volatile
    var safeSearch = true
    @Volatile
    var keywords: Set<String> = emptySet()

    fun isRunning(): Boolean = SiratVpnService.isRunning

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

    fun getPrepareIntent(context: Context): Intent? {
        return VpnService.prepare(context)
    }
}

package com.atyafcode.sirat.services.vpn

import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

object IptablesManager {

    private const val TAG = "IptablesManager"
    private const val CHAIN_NAME = "SIRAT_DNS"
    private const val REDIRECT_PORT = "5354"

    private val knownDohIps = listOf(
        "1.1.1.1", "1.0.0.1",        // Cloudflare
        "8.8.8.8", "8.8.4.4",        // Google
        "9.9.9.9", "149.112.112.112", // Quad9
        "208.67.222.222", "208.67.220.220" // OpenDNS
    )

    /** Returns true if Shizuku (ADB shell) OR Sui (root) is available */
    fun isAvailable(): Boolean {
        return isSuiAvailable() || isShizukuAvailable()
    }

    private fun isSuiAvailable(): Boolean {
        return try {
            val suiClass = Class.forName("rikka.sui.Sui")
            suiClass.getMethod("isSui").invoke(null) as Boolean
        } catch (_: Exception) {
            false
        }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns a diagnostic string of what was attempted / succeeded.
     */
    fun applyRules(): String {
        val sb = StringBuilder()
        Log.d(TAG, "Applying DNS redirect rules...")

        // 1. disable Private DNS (works with Shizuku shell)
        sb.appendLine(execChecked("settings put global private_dns_mode off"))
        sb.appendLine(execChecked("settings put global private_dns_specifier ''"))

        // 2. try iptables via Sui (root)
        if (isSuiAvailable()) {
            sb.appendLine("Sui (root) available")
            val ipt = tryIptablesRoot()
            sb.append(ipt)
            if (ipt.contains("redirect OK")) {
                val doh = blockDohIpsRoot()
                sb.append(doh)
                sb.appendLine("Shizuku mode: ACTIVE with root iptables")
                return sb.toString()
            }
        } else {
            sb.appendLine("Sui not available (device not rooted or no root granted)")
        }

        // 3. try iptables via Shizuku (shell) – usually fails without root
        if (isShizukuAvailable()) {
            sb.appendLine("Shizuku (shell) available – iptables requires root, trying anyway:")
            val ipt = tryIptablesShizuku()
            sb.append(ipt)
            if (ipt.contains("redirect OK")) {
                val doh = blockDohIpsShizuku()
                sb.append(doh)
                sb.appendLine("Shizuku mode: ACTIVE with shell iptables")
                return sb.toString()
            }
            sb.appendLine("iptables via shell rejected (CAP_NET_ADMIN missing)")
        } else {
            sb.appendLine("Shizuku not available")
        }

        // 4. fallback – run DNS server anyway (no redirect)
        sb.appendLine("DNS proxy server is running on port 5354")
        sb.appendLine("No root access – traffic will NOT be redirected")
        sb.appendLine("Use VPN mode for reliable filtering, or grant Shizuku root access via Sui")
        return sb.toString()
    }

    fun clearRules(): String {
        val sb = StringBuilder()
        try {
            sb.appendLine(execChecked("iptables -t nat -D OUTPUT -j $CHAIN_NAME"))
            sb.appendLine(execChecked("iptables -t nat -F $CHAIN_NAME"))
            sb.appendLine(execChecked("iptables -t nat -X $CHAIN_NAME"))
            sb.appendLine(execChecked("iptables -t nat -D OUTPUT -j SIRAT_DOH"))
            sb.appendLine(execChecked("iptables -t nat -F SIRAT_DOH"))
            sb.appendLine(execChecked("iptables -t nat -X SIRAT_DOH"))
        } catch (e: Exception) {
            Log.w(TAG, "clear failed", e)
            sb.appendLine("clear error: ${e.message}")
        }
        return sb.toString()
    }

    // ─── Sui (root) helpers ─────────────────────────────────────────

    private fun execRoot(command: String): String {
        val suiClass = Class.forName("rikka.sui.Sui")
        val method = suiClass.getMethod("newProcess", Array<String>::class.java)
        val process = method.invoke(null, arrayOf("sh", "-c", command)) as ShizukuRemoteProcess
        process.waitFor()
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.exitValue()
        return formatOutput(command, stdout, stderr, exitCode)
    }

    private fun tryIptablesRoot(): String {
        val sb = StringBuilder()
        sb.appendLine(execRoot("iptables -t nat -N $CHAIN_NAME"))
        sb.appendLine(execRoot("iptables -t nat -F $CHAIN_NAME"))
        val redirect = execRoot("iptables -t nat -A $CHAIN_NAME -p udp --dport 53 -j REDIRECT --to-port $REDIRECT_PORT")
        sb.appendLine(redirect)
        val jump = execRoot("iptables -t nat -A OUTPUT -j $CHAIN_NAME")
        sb.appendLine(jump)
        val ok = redirect.contains("exit: 0") && jump.contains("exit: 0")
        if (ok) sb.appendLine("<b>redirect OK</b> (root)") else sb.appendLine("<b>redirect FAILED</b> (root)")
        return sb.toString()
    }

    private fun blockDohIpsRoot(): String {
        val sb = StringBuilder()
        sb.appendLine(execRoot("iptables -t nat -N SIRAT_DOH"))
        sb.appendLine(execRoot("iptables -t nat -F SIRAT_DOH"))
        for (ip in knownDohIps) {
            sb.appendLine(execRoot("iptables -t nat -A SIRAT_DOH -d $ip -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:$REDIRECT_PORT"))
            sb.appendLine(execRoot("iptables -t nat -A SIRAT_DOH -d $ip -p tcp --dport 443 -j DROP"))
        }
        sb.appendLine(execRoot("iptables -t nat -A OUTPUT -j SIRAT_DOH"))
        sb.appendLine("<b>DoH blocking added</b> (root)")
        return sb.toString()
    }

    // ─── Shizuku (shell) helpers ──────────────────────────────────────

    private fun tryIptablesShizuku(): String {
        val sb = StringBuilder()
        sb.appendLine(execShizuku("iptables -t nat -N $CHAIN_NAME"))
        sb.appendLine(execShizuku("iptables -t nat -F $CHAIN_NAME"))
        val redirect = execShizuku("iptables -t nat -A $CHAIN_NAME -p udp --dport 53 -j REDIRECT --to-port $REDIRECT_PORT")
        sb.appendLine(redirect)
        val jump = execShizuku("iptables -t nat -A OUTPUT -j $CHAIN_NAME")
        sb.appendLine(jump)
        val ok = redirect.contains("exit: 0") && jump.contains("exit: 0")
        if (ok) sb.appendLine("<b>redirect OK</b> (shell)") else sb.appendLine("<b>redirect FAILED</b> (shell)")
        return sb.toString()
    }

    private fun blockDohIpsShizuku(): String {
        val sb = StringBuilder()
        sb.appendLine(execShizuku("iptables -t nat -N SIRAT_DOH"))
        sb.appendLine(execShizuku("iptables -t nat -F SIRAT_DOH"))
        for (ip in knownDohIps) {
            sb.appendLine(execShizuku("iptables -t nat -A SIRAT_DOH -d $ip -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:$REDIRECT_PORT"))
            sb.appendLine(execShizuku("iptables -t nat -A SIRAT_DOH -d $ip -p tcp --dport 443 -j DROP"))
        }
        sb.appendLine(execShizuku("iptables -t nat -A OUTPUT -j SIRAT_DOH"))
        sb.appendLine("<b>DoH blocking added</b> (shell)")
        return sb.toString()
    }

    private fun execShizuku(command: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val process = m.invoke(
            null, arrayOf("sh", "-c", command), null, "/"
        ) as ShizukuRemoteProcess
        process.waitFor()
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exitCode = process.exitValue()
        return formatOutput(command, stdout, stderr, exitCode)
    }

    // ─── Shared helpers ───────────────────────────────────────────────

    private fun execChecked(command: String): String {
        try {
            if (isSuiAvailable()) return execRoot(command)
            if (isShizukuAvailable()) return execShizuku(command)
            return "<i>No privileged shell available</i>"
        } catch (e: Exception) {
            return "$command -> exception: ${e.message}"
        }
    }

    private fun execCheckedNdc(command: String): String {
        try {
            if (isSuiAvailable()) return execRoot(command)
            if (isShizukuAvailable()) return execShizuku(command)
            return "<i>No privileged shell available</i>"
        } catch (e: Exception) {
            return "$command -> exception: ${e.message}"
        }
    }

    private fun formatOutput(cmd: String, stdout: String, stderr: String, exitCode: Int): String {
        val parts = mutableListOf<String>()
        if (stdout.isNotBlank()) parts.add("out: ${stdout.trim()}")
        if (stderr.isNotBlank()) parts.add("err: ${stderr.trim()}")
        parts.add("exit: $exitCode")
        val combined = parts.joinToString(" | ")
        if (exitCode != 0) Log.w(TAG, "[$cmd] $combined")
        else Log.d(TAG, "[$cmd] $combined")
        return "$cmd → $combined"
    }
}

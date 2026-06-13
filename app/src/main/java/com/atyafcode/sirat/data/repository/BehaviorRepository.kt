package com.atyafcode.sirat.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate

data class BehaviorLog(
    val date: LocalDate,
    val count: Int,
    val reason: String
)

class BehaviorRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLog(log: BehaviorLog) {
        val key = log.date.toString()
        prefs.edit {
            putString("${key}_reason", log.reason)
            putInt("${key}_count", log.count)
        }
    }

    fun getLog(date: LocalDate): BehaviorLog? {
        val key = date.toString()
        if (!prefs.contains("${key}_count")) return null
        
        val count = prefs.getInt("${key}_count", 0)
        val reason = prefs.getString("${key}_reason", "") ?: ""
        
        return BehaviorLog(date, count, reason)
    }

    companion object {
        private const val PREFS_NAME = "behavior_logs"
    }
}

package com.atyafcode.sirat.data.filter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_logs")
data class BlockedLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

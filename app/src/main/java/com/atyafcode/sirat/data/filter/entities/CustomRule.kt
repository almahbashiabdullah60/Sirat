package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_rules")
data class CustomRule(
    @PrimaryKey val domain: String,
    val isWhitelist: Boolean
)

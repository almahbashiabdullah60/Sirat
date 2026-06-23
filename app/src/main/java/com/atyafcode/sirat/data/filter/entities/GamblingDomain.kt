package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gambling")
data class GamblingDomain(
    @PrimaryKey val domain: String
)

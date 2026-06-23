package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social")
data class SocialDomain(
    @PrimaryKey val domain: String
)

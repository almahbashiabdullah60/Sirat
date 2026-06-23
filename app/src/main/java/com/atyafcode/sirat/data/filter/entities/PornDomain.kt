package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "porn")
data class PornDomain(
    @PrimaryKey val domain: String
)

package com.atyafcode.sirat.data.filter

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.atyafcode.sirat.data.filter.entities.CustomRule
import com.atyafcode.sirat.data.filter.entities.GamblingDomain
import com.atyafcode.sirat.data.filter.entities.PornDomain
import com.atyafcode.sirat.data.filter.entities.SocialDomain

@Database(
    entities = [PornDomain::class, GamblingDomain::class, SocialDomain::class, CustomRule::class, BlockedLog::class],
    version = 1,
    exportSchema = false
)
abstract class FilterDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao

    companion object {
        @Volatile
        private var INSTANCE: FilterDatabase? = null

        fun getInstance(app: Application): FilterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    app,
                    FilterDatabase::class.java,
                    "sirat_filter.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

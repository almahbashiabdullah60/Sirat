package com.atyafcode.sirat.data.filter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atyafcode.sirat.data.filter.entities.CustomRule
import com.atyafcode.sirat.data.filter.entities.GamblingDomain
import com.atyafcode.sirat.data.filter.entities.PornDomain
import com.atyafcode.sirat.data.filter.entities.SocialDomain

@Dao
interface FilterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPorn(domains: List<PornDomain>)

    @Query("SELECT domain FROM porn")
    suspend fun getAllPorn(): List<String>

    @Query("DELETE FROM porn")
    suspend fun clearPorn()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGambling(domains: List<GamblingDomain>)

    @Query("SELECT domain FROM gambling")
    suspend fun getAllGambling(): List<String>

    @Query("DELETE FROM gambling")
    suspend fun clearGambling()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSocial(domains: List<SocialDomain>)

    @Query("SELECT domain FROM social")
    suspend fun getAllSocial(): List<String>

    @Query("DELETE FROM social")
    suspend fun clearSocial()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomRule(rule: CustomRule)

    @Query("SELECT * FROM custom_rules")
    suspend fun getAllCustomRules(): List<CustomRule>

    @Query("DELETE FROM custom_rules WHERE domain = :domain")
    suspend fun deleteCustomRule(domain: String)

    @Insert
    suspend fun insertLog(log: BlockedLog)

    @Query("SELECT * FROM blocked_logs ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentLogs(): List<BlockedLog>

    @Query("DELETE FROM blocked_logs")
    suspend fun clearLogs()
}

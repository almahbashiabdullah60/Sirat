package com.atyafcode.sirat.data.filter

import android.content.Context
import com.atyafcode.sirat.data.filter.entities.GamblingDomain
import com.atyafcode.sirat.data.filter.entities.PornDomain
import com.atyafcode.sirat.data.filter.entities.SocialDomain
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SyncManager(
    private val context: Context,
    private val database: FilterDatabase,
    private val repository: FilterRepository
) {
    suspend fun syncSelected(blockPorn: Boolean, blockGambling: Boolean, blockSocial: Boolean) = withContext(Dispatchers.IO) {
        val dao = database.filterDao()

        if (blockPorn) {
            syncCategory("filter/porn.json") { list ->
                dao.clearPorn()
                dao.insertPorn(list.map { PornDomain(it) })
            }
            repository.loadPornCache()
        } else {
            repository.clearPornCache()
        }

        if (blockGambling) {
            syncCategory("filter/gambling.json") { list ->
                dao.clearGambling()
                dao.insertGambling(list.map { GamblingDomain(it) })
            }
            repository.loadGamblingCache()
        } else {
            repository.clearGamblingCache()
        }

        if (blockSocial) {
            syncCategory("filter/social.json") { list ->
                dao.clearSocial()
                dao.insertSocial(list.map { SocialDomain(it) })
            }
            repository.loadSocialCache()
        } else {
            repository.clearSocialCache()
        }
    }

    private fun syncCategory(assetPath: String, inserter: suspend (List<String>) -> Unit) {
        try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val domains = JSONArray(json).let { arr ->
                (0 until arr.length()).map { i ->
                    arr.getString(i).trim().removePrefix("0.0.0.0 ").lowercase()
                }
            }
            runBlocking { inserter(domains) }
        } catch (_: Exception) {
        }
    }
}

package com.atyafcode.sirat.data.repository

import com.atyafcode.sirat.data.filter.FilterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilterRepository(private val database: FilterDatabase) {

    private var pornCache = HashSet<String>()
    private var gamblingCache = HashSet<String>()
    private var socialCache = HashSet<String>()
    private var whitelistCache = HashSet<String>()
    private var blacklistCache = HashSet<String>()
    private var keywordCache = HashSet<String>()

    suspend fun loadCaches() = withContext(Dispatchers.IO) {
        val dao = database.filterDao()
        pornCache = dao.getAllPorn().toHashSet()
        gamblingCache = dao.getAllGambling().toHashSet()
        socialCache = dao.getAllSocial().toHashSet()

        val rules = dao.getAllCustomRules()
        whitelistCache = rules.filter { it.isWhitelist }.map { it.domain }.toHashSet()
        blacklistCache = rules.filter { !it.isWhitelist }.map { it.domain }.toHashSet()
    }

    fun setKeywords(keywords: Set<String>) {
        keywordCache = keywords.toHashSet()
    }

    fun shouldBlockDomain(
        domain: String,
        blockPorn: Boolean = true,
        blockGambling: Boolean = true,
        blockSocial: Boolean = true,
        useKeywords: Boolean = true
    ): Boolean {
        val cleanDomain = domain.lowercase()

        if (cleanDomain in whitelistCache) return false
        if (cleanDomain in blacklistCache) return true
        if (useKeywords && keywordCache.any { cleanDomain.contains(it) }) return true
        if (blockPorn && cleanDomain in pornCache) return true
        if (blockGambling && cleanDomain in gamblingCache) return true
        if (blockSocial && cleanDomain in socialCache) return true

        return false
    }
}

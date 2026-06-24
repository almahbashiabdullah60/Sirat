package com.atyafcode.sirat.features.filtering.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.filter.SyncManager
import com.atyafcode.sirat.data.filter.entities.CustomRule
import com.atyafcode.sirat.data.repository.FilterRepository
import com.atyafcode.sirat.services.vpn.DnsFilterController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FilteringViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FilterDatabase.getInstance(application)
    val filterRepo = FilterRepository(db)
    private val syncManager = SyncManager(application, db, filterRepo)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _blockPorn = MutableStateFlow(true)
    val blockPorn: StateFlow<Boolean> = _blockPorn

    private val _blockGambling = MutableStateFlow(true)
    val blockGambling: StateFlow<Boolean> = _blockGambling

    private val _blockSocial = MutableStateFlow(false)
    val blockSocial: StateFlow<Boolean> = _blockSocial

    private val _safeSearch = MutableStateFlow(true)
    val safeSearch: StateFlow<Boolean> = _safeSearch

    private val _logs = MutableStateFlow<List<BlockedLog>>(emptyList())
    val logs: StateFlow<List<BlockedLog>> = _logs

    private val _blockCount = MutableStateFlow(0)
    val blockCount: StateFlow<Int> = _blockCount

    private val _keywords = MutableStateFlow<Set<String>>(emptySet())
    val keywords: StateFlow<Set<String>> = _keywords

    private val _customRules = MutableStateFlow<List<CustomRule>>(emptyList())
    val customRules: StateFlow<List<CustomRule>> = _customRules

    fun refreshState() {
        _active.value = DnsFilterController.isRunning()
        loadLogs()
        loadBlockCount()
        loadCustomRules()
        _keywords.value = DnsFilterController.keywords
    }

    fun start(context: android.content.Context) {
        syncFlags()
        DnsFilterController.start(context)
        _active.value = true
    }

    fun stop(context: android.content.Context) {
        DnsFilterController.stop(context)
        _active.value = false
    }

    private fun syncFlags() {
        DnsFilterController.blockPorn = _blockPorn.value
        DnsFilterController.blockGambling = _blockGambling.value
        DnsFilterController.blockSocial = _blockSocial.value
        DnsFilterController.safeSearch = _safeSearch.value
        DnsFilterController.keywords = _keywords.value
    }

    fun setBlockPorn(enabled: Boolean) { _blockPorn.value = enabled }
    fun setBlockGambling(enabled: Boolean) { _blockGambling.value = enabled }
    fun setBlockSocial(enabled: Boolean) { _blockSocial.value = enabled }
    fun setSafeSearch(enabled: Boolean) { _safeSearch.value = enabled }

    fun loadLogs() {
        viewModelScope.launch {
            _logs.value = db.filterDao().getRecentLogs()
        }
    }

    fun loadBlockCount() {
        viewModelScope.launch {
            _blockCount.value = db.filterDao().getLogCount()
        }
    }

    fun syncDomainLists() {
        viewModelScope.launch {
            syncManager.syncAll()
        }
    }

    // ── Keywords ──

    fun addKeyword(keyword: String) {
        val updated = _keywords.value + keyword.lowercase().trim()
        _keywords.value = updated
        DnsFilterController.keywords = updated
        filterRepo.setKeywords(updated)
    }

    fun removeKeyword(keyword: String) {
        val updated = _keywords.value - keyword
        _keywords.value = updated
        DnsFilterController.keywords = updated
        filterRepo.setKeywords(updated)
    }

    // ── Custom rules ──

    fun loadCustomRules() {
        viewModelScope.launch {
            _customRules.value = db.filterDao().getAllCustomRules()
        }
    }

    fun addCustomRule(domain: String, isWhitelist: Boolean) {
        viewModelScope.launch {
            val rule = CustomRule(domain.lowercase().trim(), isWhitelist)
            db.filterDao().insertCustomRule(rule)
            loadCustomRules()
            filterRepo.loadCaches()
        }
    }

    fun deleteCustomRule(domain: String) {
        viewModelScope.launch {
            db.filterDao().deleteCustomRule(domain)
            loadCustomRules()
            filterRepo.loadCaches()
        }
    }
}

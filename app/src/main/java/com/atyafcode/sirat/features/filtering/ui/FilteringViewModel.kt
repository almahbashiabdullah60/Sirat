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
    val filterRepo = FilterRepository.getInstance(db)
    private val syncManager = SyncManager(application, db, filterRepo)
    private val prefs = application.getSharedPreferences("filter_prefs", 0)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _blockPorn = MutableStateFlow(true)
    val blockPorn: StateFlow<Boolean> = _blockPorn

    private val _blockGambling = MutableStateFlow(prefs.getBoolean("blockGambling", true))
    val blockGambling: StateFlow<Boolean> = _blockGambling

    private val _blockSocial = MutableStateFlow(prefs.getBoolean("blockSocial", false))
    val blockSocial: StateFlow<Boolean> = _blockSocial

    private val _safeSearch = MutableStateFlow(prefs.getBoolean("safeSearch", true))
    val safeSearch: StateFlow<Boolean> = _safeSearch

    private val _logs = MutableStateFlow<List<BlockedLog>>(emptyList())
    val logs: StateFlow<List<BlockedLog>> = _logs

    private val _blockCount = MutableStateFlow(0)
    val blockCount: StateFlow<Int> = _blockCount

    private val _keywords = MutableStateFlow<Set<String>>(emptySet())
    val keywords: StateFlow<Set<String>> = _keywords

    private val _customRules = MutableStateFlow<List<CustomRule>>(emptyList())
    val customRules: StateFlow<List<CustomRule>> = _customRules

    init {
        syncFlags()
    }

    fun refreshState() {
        _active.value = DnsFilterController.isRunning()
        loadLogs()
        loadBlockCount()
        loadCustomRules()
        _keywords.value = DnsFilterController.keywords
    }

    fun start(context: android.content.Context) {
        syncFlags()
        viewModelScope.launch {
            syncManager.syncSelected(_blockPorn.value, _blockGambling.value, _blockSocial.value)
            DnsFilterController.start(context)
            _active.value = true
        }
    }

    fun stop(context: android.content.Context) {
        DnsFilterController.stop(context)
        _active.value = false
        viewModelScope.launch {
            db.filterDao().clearLogs()
            filterRepo.clearPornCache()
            filterRepo.clearGamblingCache()
            filterRepo.clearSocialCache()
        }
        resetBlockCount()
    }

    private fun syncFlags() {
        DnsFilterController.blockPorn = _blockPorn.value
        DnsFilterController.blockGambling = _blockGambling.value
        DnsFilterController.blockSocial = _blockSocial.value
        DnsFilterController.safeSearch = _safeSearch.value
        DnsFilterController.keywords = _keywords.value
    }

    private fun persistFlags() {
        prefs.edit()
            .putBoolean("blockPorn", _blockPorn.value)
            .putBoolean("blockGambling", _blockGambling.value)
            .putBoolean("blockSocial", _blockSocial.value)
            .putBoolean("safeSearch", _safeSearch.value)
            .apply()
    }

    fun setBlockPorn(enabled: Boolean) {
        // Mandatory, do nothing
    }

    fun setBlockGambling(enabled: Boolean) {
        _blockGambling.value = enabled
        DnsFilterController.blockGambling = enabled
        persistFlags()
    }

    fun setBlockSocial(enabled: Boolean) {
        _blockSocial.value = enabled
        DnsFilterController.blockSocial = enabled
        persistFlags()
    }

    fun setSafeSearch(enabled: Boolean) {
        _safeSearch.value = enabled
        DnsFilterController.safeSearch = enabled
        persistFlags()
    }

    private fun resetBlockCount() {
        _blockCount.value = 0
        _logs.value = emptyList()
    }

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

package com.atyafcode.sirat.features.filtering.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.data.filter.BlockedLog
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.filter.SyncManager
import com.atyafcode.sirat.data.repository.FilterRepository
import com.atyafcode.sirat.services.vpn.SiratVpnService
import com.atyafcode.sirat.services.vpn.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FilteringViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FilterDatabase.getInstance(application)
    val filterRepo = FilterRepository(db)
    private val syncManager = SyncManager(application, db, filterRepo)

    private val _vpnRunning = MutableStateFlow(false)
    val vpnRunning: StateFlow<Boolean> = _vpnRunning

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

    fun refreshVpnState() {
        _vpnRunning.value = VpnController.isVpnRunning()
    }

    fun toggleVpn() {
        val context = getApplication<Application>()
        if (_vpnRunning.value) {
            VpnController.stop(context)
        } else {
            VpnController.start(context)
        }
        _vpnRunning.value = !_vpnRunning.value
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

    fun syncDomainLists() {
        viewModelScope.launch {
            syncManager.syncAll()
        }
    }
}

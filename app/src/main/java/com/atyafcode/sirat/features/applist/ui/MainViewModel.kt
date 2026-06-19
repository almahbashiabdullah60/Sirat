package com.atyafcode.sirat.features.applist.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.data.repository.AppLockRepository
import com.atyafcode.sirat.features.applist.domain.AppSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val applicationInfo: ApplicationInfo,
    val label: String,
    val packageName: String = applicationInfo.packageName
)

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appSearchManager = AppSearchManager(application)
    private val appLockRepository = AppLockRepository(application)
    private val packageManager = application.packageManager

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _lockedApps = MutableStateFlow<Set<String>>(emptySet())

    private val _debouncedQuery = MutableStateFlow("")

    val lockedAppsFlow: StateFlow<List<AppItem>> =
        combine(_allApps, _lockedApps, _debouncedQuery) { apps, locked, query ->
            withContext(Dispatchers.Default) {
                apps.filter { it.packageName in locked }
                    .filter { it.matchesQuery(query) }
                    .sortedBy { it.label }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val unlockedAppsFlow: StateFlow<List<AppItem>> =
        combine(_allApps, _lockedApps, _debouncedQuery) { apps, locked, query ->
            withContext(Dispatchers.Default) {
                apps.filterNot { it.packageName in locked }
                    .filter { it.matchesQuery(query) }
                    .sortedBy { it.label }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private fun AppItem.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return label.contains(query, ignoreCase = true)
    }

    init {
        loadAllApplications()
        loadLockedApps()

        viewModelScope.launch {
            _searchQuery
                .debounce(100L)
                .collect { query ->
                    _debouncedQuery.value = query
                }
        }
    }

    private fun loadAllApplications() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val rawApps = appSearchManager.loadApps(true)
                    rawApps.map { info ->
                        val label = try {
                            info.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            info.packageName
                        }
                        AppItem(info, label)
                    }
                }
                _allApps.value = apps
            } catch (_: Exception) {
                _allApps.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadLockedApps() {
        _lockedApps.value = appLockRepository.getLockedApps()
    }

    fun lockApps(packageNames: List<String>) {
        appLockRepository.addMultipleLockedApps(packageNames.toSet())
        _lockedApps.value = appLockRepository.getLockedApps()
    }

    fun unlockApp(packageName: String) {
        appLockRepository.removeLockedApp(packageName)
        _lockedApps.value = appLockRepository.getLockedApps()
    }
}


package com.atyafcode.sirat.features.contentdetection.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.appLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel لشاشة إعدادات الكشف البصري عن المحتوى.
 */
class ContentDetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val appLockRepository = application.appLockRepository()

    private val _isEnabled = MutableStateFlow(appLockRepository.isContentDetectionEnabled())
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _scanInterval = MutableStateFlow(appLockRepository.getContentDetectionScanInterval())
    val scanInterval: StateFlow<Long> = _scanInterval.asStateFlow()

    private val _threshold = MutableStateFlow(appLockRepository.getContentDetectionThreshold())
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    private val _excludedApps = MutableStateFlow(appLockRepository.getContentDetectionExcludedApps())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        appLockRepository.setContentDetectionEnabled(enabled)
        _isEnabled.value = enabled
    }

    fun setScanInterval(intervalMs: Long) {
        appLockRepository.setContentDetectionScanInterval(intervalMs)
        _scanInterval.value = intervalMs
    }

    fun setThreshold(value: Float) {
        appLockRepository.setContentDetectionThreshold(value)
        _threshold.value = value
    }

    fun addExcludedApp(packageName: String) {
        val current = _excludedApps.value.toMutableSet()
        current.add(packageName)
        appLockRepository.setContentDetectionExcludedApps(current)
        _excludedApps.value = current
    }

    fun removeExcludedApp(packageName: String) {
        val current = _excludedApps.value.toMutableSet()
        current.remove(packageName)
        appLockRepository.setContentDetectionExcludedApps(current)
        _excludedApps.value = current
    }
}

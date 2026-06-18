package com.atyafcode.sirat.features.aisettings.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.planRepository
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.CloudAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.DownloadStatus
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.OpenRouterModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AISettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val planRepo = application.planRepository()
    private val localAI = LocalAIProvider(application)
    private val cloudAI = CloudAIProvider()

    val aiProvider = MutableStateFlow(planRepo.getAIProvider())
    val cloudProvider = MutableStateFlow(planRepo.getCloudProvider())
    val apiKey = MutableStateFlow(planRepo.getApiKey())
    val selectedModel = MutableStateFlow(planRepo.getOpenRouterModel())
    val planLanguage = MutableStateFlow(planRepo.getPlanLanguage())
    val religion = MutableStateFlow(planRepo.getReligion())

    private val _isModelDownloaded = MutableStateFlow(localAI.isModelDownloaded() && planRepo.isModelCompleted())
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded

    private val _downloadStatus = MutableStateFlow(DownloadStatus())
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModel>> = _openRouterModels

    private var downloadPollingJob: Job? = null
    private var lastFetchedKey: String? = null

    init {
        if (apiKey.value.isNotBlank()) {
            refreshModels()
        }
        
        viewModelScope.launch {
            val savedId = planRepo.getDownloadId()
            val completed = localAI.isModelDownloaded() && planRepo.isModelCompleted()
            _isModelDownloaded.value = completed

            if (!completed) {
                val currentStatus = localAI.getDownloadStatus(savedId)
                if (currentStatus.isRunning) {
                    _downloadStatus.value = currentStatus
                    planRepo.setDownloadId(currentStatus.id)
                    startDownloadPolling(currentStatus.id)
                }
            }
        }
    }

    fun saveSettings() {
        planRepo.setAIProvider(aiProvider.value)
        planRepo.setCloudProvider(cloudProvider.value)
        planRepo.setApiKey(apiKey.value)
        planRepo.setOpenRouterModel(selectedModel.value)
        planRepo.setPlanLanguage(planLanguage.value)
        planRepo.setReligion(religion.value)
    }

    fun refreshModels(force: Boolean = false) {
        if (!force && apiKey.value == lastFetchedKey && _openRouterModels.value.isNotEmpty()) {
            return
        }
        
        viewModelScope.launch {
            if (apiKey.value.isNotBlank()) {
                val models = cloudAI.fetchOpenRouterModels(apiKey.value)
                if (models.isNotEmpty()) {
                    _openRouterModels.value = models
                    lastFetchedKey = apiKey.value
                }
            }
        }
    }

    fun downloadModel() {
        val id = localAI.downloadModel()
        if (id >= 0) {
            planRepo.setDownloadId(id)
            planRepo.setModelCompleted(false)
            startDownloadPolling(id)
        }
    }

    fun cancelDownload() {
        val id = planRepo.getDownloadId()
        if (id != -1L) {
            localAI.cancelDownload(id)
            planRepo.setDownloadId(-1L)
            _downloadStatus.value = DownloadStatus()
            downloadPollingJob?.cancel()
        }
    }

    private fun startDownloadPolling(id: Long) {
        downloadPollingJob?.cancel()
        downloadPollingJob = viewModelScope.launch {
            while (true) {
                val status = localAI.getDownloadStatus(id)
                _downloadStatus.value = status
                
                if (status.isCompleted) {
                    _isModelDownloaded.value = true
                    planRepo.setModelCompleted(true)
                    planRepo.setDownloadId(-1L)
                    break
                }
                
                if (status.isFailed) {
                    planRepo.setDownloadId(-1L)
                    break
                }
                
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadPollingJob?.cancel()
    }
}

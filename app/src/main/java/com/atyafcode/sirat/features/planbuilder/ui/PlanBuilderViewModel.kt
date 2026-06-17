package com.atyafcode.sirat.features.planbuilder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.behaviorRepository
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
import java.time.LocalDate

sealed class PlanUIState {
    object Idle : PlanUIState()
    data class Loading(val message: String = "جاري الاتصال بالمحرك الذكي...") : PlanUIState()
    data class Success(val plan: String) : PlanUIState()
    data class Error(val message: String) : PlanUIState()
}

class PlanBuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val planRepo = application.planRepository()
    private val behaviorRepo = application.behaviorRepository()
    
    private val localAI = LocalAIProvider(application)
    private val cloudAI = CloudAIProvider()

    private val _uiState = MutableStateFlow<PlanUIState>(PlanUIState.Idle)
    val uiState: StateFlow<PlanUIState> = _uiState

    private val _isModelDownloaded = MutableStateFlow(localAI.isModelDownloaded() && planRepo.isModelCompleted())
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded

    private val _downloadStatus = MutableStateFlow(DownloadStatus())
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus

    private val _openRouterModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterModels: StateFlow<List<OpenRouterModel>> = _openRouterModels

    private var downloadPollingJob: Job? = null
    private var generationJob: Job? = null

    val planLanguage = MutableStateFlow(planRepo.getPlanLanguage())
    val religion = MutableStateFlow(planRepo.getReligion())
    val aiProvider = MutableStateFlow(planRepo.getAIProvider())
    val cloudProvider = MutableStateFlow(planRepo.getCloudProvider())
    val apiKey = MutableStateFlow(planRepo.getApiKey())
    val selectedModel = MutableStateFlow(planRepo.getOpenRouterModel())

    // Behavioral Goals State
    val goalType = MutableStateFlow("quit") // "quit" or "commit"
    val selectedBehavior = MutableStateFlow("")
    
    val availableBehaviors = MutableStateFlow<List<String>>(emptyList())

    init {
        val savedPlan = planRepo.getPlan()
        if (!savedPlan.isNullOrBlank()) {
            _uiState.value = PlanUIState.Success(savedPlan)
        }

        // Setup dynamic behaviors based on initial state
        updateBehaviors()
        
        // Fetch models if OpenRouter is selected
        if (apiKey.value.isNotBlank()) {
            refreshModels()
        }
        
        // Robust check for ongoing downloads
        viewModelScope.launch {
            val savedId = planRepo.getDownloadId()
            android.util.Log.d("SiratAI", "ViewModel Init: Saved ID = $savedId")
            
            // Strictly check for completion
            val completed = localAI.isModelDownloaded() && planRepo.isModelCompleted()
            _isModelDownloaded.value = completed
            android.util.Log.d("SiratAI", "Is Model Fully Downloaded: $completed")

            if (!completed) {
                // Look for active download regardless of savedId
                val currentStatus = localAI.getDownloadStatus(savedId)
                android.util.Log.d("SiratAI", "Current Status: isRunning=${currentStatus.isRunning}, id=${currentStatus.id}")
                
                if (currentStatus.isRunning) {
                    _downloadStatus.value = currentStatus
                    planRepo.setDownloadId(currentStatus.id)
                    startDownloadPolling(currentStatus.id)
                } else if (currentStatus.isFailed) {
                    _downloadStatus.value = currentStatus
                } else if (!localAI.isModelDownloaded()) {
                    // Not downloading and file doesn't exist/too small
                    planRepo.setModelCompleted(false)
                }
            }
        }
    }

    fun buildPlan() {
        if (aiProvider.value == PlanRepository.AI_PROVIDER_LOCAL && !_isModelDownloaded.value) {
            _uiState.value = PlanUIState.Error(getApplication<Application>().getString(R.string.plan_error_local_engine))
            return
        }
        
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = PlanUIState.Loading(getApplication<Application>().getString(R.string.chat_loading_message))
            
            val statusJob = launch {
                val loadingMessages = listOf(
                    getApplication<Application>().getString(R.string.main_screen_ai_analysis_logs),
                    getApplication<Application>().getString(R.string.main_screen_ai_crafting_steps),
                    getApplication<Application>().getString(R.string.main_screen_ai_spiritual_tips),
                    getApplication<Application>().getString(R.string.main_screen_ai_formatting),
                    getApplication<Application>().getString(R.string.main_screen_ai_final_touches)
                )
                var index = 0
                while (true) {
                    delay(4000)
                    _uiState.value = PlanUIState.Loading(loadingMessages[index % loadingMessages.size])
                    index++
                }
            }

            // Save current settings
            planRepo.setPlanLanguage(planLanguage.value)
            planRepo.setReligion(religion.value)
            planRepo.setAIProvider(aiProvider.value)
            planRepo.setCloudProvider(cloudProvider.value)
            planRepo.setApiKey(apiKey.value)
            planRepo.setOpenRouterModel(selectedModel.value)

            val prompt = constructPrompt()
            
            val result = try {
                if (aiProvider.value == PlanRepository.AI_PROVIDER_LOCAL) {
                    if (localAI.initialize()) {
                        localAI.generateResponse(prompt)
                    } else {
                        getApplication<Application>().getString(R.string.plan_error_init_local)
                    }
                } else {
                    if (apiKey.value.isBlank()) {
                        getApplication<Application>().getString(R.string.plan_error_no_api_key)
                    } else {
                        cloudAI.generateResponse(
                            provider = cloudProvider.value,
                            apiKey = apiKey.value,
                            prompt = prompt,
                            model = selectedModel.value
                        )
                    }
                }
            } catch (e: Exception) {
                getApplication<Application>().getString(R.string.plan_error_unexpected, e.localizedMessage ?: "")
            }

            statusJob.cancel()
            if (result.contains("فشل") || result.contains("خطأ") || result.isBlank() || 
                result == getApplication<Application>().getString(R.string.plan_error_init_local) ||
                result == getApplication<Application>().getString(R.string.plan_error_no_api_key)) {
                _uiState.value = PlanUIState.Error(result.ifBlank { getApplication<Application>().getString(R.string.plan_error_empty_response) })
            } else {
                planRepo.savePlan(result)
                _uiState.value = PlanUIState.Success(result)
            }
        }
    }

    private var lastFetchedKey: String? = null

    fun refreshModels(force: Boolean = false) {
        if (!force && apiKey.value == lastFetchedKey && _openRouterModels.value.isNotEmpty()) {
            return // البيانات موجودة بالفعل لنفس المفتاح، لا داعي للجلب مرة أخرى
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

    fun updateBehaviors() {
        val type = goalType.value
        val rel = religion.value.lowercase()
        
        val list = if (type == "quit") {
            listOf("التدخين", "المخدرات", "الإباحية", "العادة السرية", "إدمان الهاتف", "تضييع الوقت")
        } else {
            if (rel.contains("إسلام") || rel.contains("islam")) {
                listOf("الصلاة في المسجد", "صلاة الفجر", "الأذكار", "التسبيح", "قراءة القرآن", "بر الوالدين", "صدقة")
            } else {
                listOf("الرياضة اليومية", "القراءة", "الاستيقاظ مبكراً", "تنظيم الوقت", "العمل التطوعي")
            }
        }
        availableBehaviors.value = list
        // Don't auto-set behavior if it's already set or empty
        if (selectedBehavior.value.isEmpty()) {
            selectedBehavior.value = list.firstOrNull() ?: ""
        }
    }

    private fun constructPrompt(): String {
        // Take only last 15 days of logs to reduce prompt size and memory pressure
        val last15DaysLogs = behaviorRepo.getLogsForRange(LocalDate.now().minusDays(15), LocalDate.now())
        val behaviorSummary = if (last15DaysLogs.isEmpty()) getApplication<Application>().getString(R.string.chat_no_logs)
        else last15DaysLogs.joinToString("\n") { 
            "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
        }

        val typeText = if (goalType.value == "quit") getApplication<Application>().getString(R.string.plan_goal_quit) + ": " 
                       else getApplication<Application>().getString(R.string.plan_goal_commit) + ": "
        val targetBehavior = selectedBehavior.value

        return """
            لغة الخطة المطلوبة: ${if (planLanguage.value == "ar") "العربية" else "English"}
            ديانة المستخدم: ${religion.value}
            الهدف الأساسي: $typeText $targetBehavior
            
            سجلات السلوك لآخر 15 يوم:
            $behaviorSummary
            
            بناءً على هذه البيانات، قم ببناء خطة تعافي مخصصة، عملية جداً، ومختصرة للغاية (Maximum 2-3 pages).
            يجب أن تتضمن الخطة الأقسام التالية بتركيز شديد:
            1. تحليل سريع (نقطتان فقط) لأنماط السلوك.
            2. أهم 5 خطوات عملية يومية للتنفيذ الفوري.
            3. نصيحة روحية/دينية واحدة قوية.
            4. أهم البدائل الصحية المقترحة (3 بدائل فقط).
            
            تعليمات التنسيق:
            - استخدم لغة مباشرة وعملية (أفعال أمر).
            - تجنب المقدمات الطويلة والعبارات الإنشائية.
            - يجب أن تكون الخطة كاملة ومفيدة ولكن في أقل عدد ممكن من الكلمات.
            - الهدف أن تكون الخطة "مختصرة ومركزة" (Executive Summary style).
        """.trimIndent()
    }

    fun downloadModel() {
        val id = localAI.downloadModel()
        if (id >= 0) {
            planRepo.setDownloadId(id)
            planRepo.setModelCompleted(false)
            startDownloadPolling(id)
        } else if (id == -2L) {
            // Already downloaded
            checkModelStatus()
        }
        _uiState.value = PlanUIState.Idle // Clear any errors
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
                    planRepo.setDownloadId(-1L) // Clear it
                    break
                }
                
                if (status.isFailed) {
                    planRepo.setDownloadId(-1L)
                    break
                }
                
                if (!status.isRunning && !status.isCompleted && !status.isFailed) {
                    // Download might have been canceled or lost
                    break
                }

                delay(1000) // Poll every second
            }
        }
    }

    fun checkModelStatus() {
        _isModelDownloaded.value = localAI.isModelDownloaded()
    }

    override fun onCleared() {
        super.onCleared()
        localAI.close()
        generationJob?.cancel()
        downloadPollingJob?.cancel()
    }
}

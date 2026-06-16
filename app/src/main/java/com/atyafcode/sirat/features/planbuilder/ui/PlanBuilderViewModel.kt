package com.atyafcode.sirat.features.planbuilder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    object Loading : PlanUIState()
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
            _uiState.value = PlanUIState.Error("يرجى تنزيل محرك الذكاء الاصطناعي أولاً أو استخدام المحرك السحابي")
            return
        }
        
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = PlanUIState.Loading
            
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
                        "فشل في تهيئة الذكاء الاصطناعي المحلي. تأكد من وجود مساحة كافية في الذاكرة (RAM)."
                    }
                } else {
                    if (apiKey.value.isBlank()) {
                        "يرجى إدخال مفتاح API للمحرك السحابي"
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
                "خطأ غير متوقع: ${e.localizedMessage}"
            }

            if (result.contains("فشل") || result.contains("خطأ") || result.isBlank()) {
                _uiState.value = PlanUIState.Error(result.ifBlank { "تلقى التطبيق رداً فارغاً من المحرك. حاول مرة أخرى." })
            } else {
                planRepo.savePlan(result)
                _uiState.value = PlanUIState.Success(result)
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            if (apiKey.value.isNotBlank()) {
                _openRouterModels.value = cloudAI.fetchOpenRouterModels(apiKey.value)
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
        if (!list.contains(selectedBehavior.value)) {
            selectedBehavior.value = list.firstOrNull() ?: ""
        }
    }

    private fun constructPrompt(): String {
        // Take only last 15 days of logs to reduce prompt size and memory pressure
        val last15DaysLogs = behaviorRepo.getLogsForRange(LocalDate.now().minusDays(15), LocalDate.now())
        val behaviorSummary = last15DaysLogs.joinToString("\n") { 
            "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
        }

        val typeText = if (goalType.value == "quit") "التخلص من سلوك: " else "الالتزام بسلوك: "
        val targetBehavior = selectedBehavior.value

        return """
            لغة الخطة المطلوبة: ${if (planLanguage.value == "ar") "العربية" else "الإنجليزية"}
            ديانة المستخدم: ${religion.value}
            الهدف الأساسي: $typeText $targetBehavior
            
            سجلات السلوك لآخر 15 يوم:
            $behaviorSummary
            
            بناءً على هذه البيانات، قم ببناء خطة تعافي مخصصة وعملية تتضمن:
            1. تحليل لأنماط السلوك ونقاط الضعف المتعلقة بـ ($targetBehavior).
            2. خطوات عملية يومية محددة لـ ($targetBehavior).
            3. نصائح روحية ودينية بناءً على ديانة المستخدم المذكورة أعلاه لتقوية الإرادة.
            4. بدائل صحية مقترحة.
            
            اجعل الخطة مشجعة، احترافية، وسهلة التنفيذ، ومفصلة قدر الإمكان.
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

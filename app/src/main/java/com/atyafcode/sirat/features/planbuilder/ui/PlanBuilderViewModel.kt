package com.atyafcode.sirat.features.planbuilder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.R
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.core.utils.planRepository
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.CloudAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
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

    private var generationJob: Job? = null

    // Behavioral Goals State
    val goalType = MutableStateFlow("quit") // "quit" or "commit"
    val selectedBehavior = MutableStateFlow("")
    val availableBehaviors = MutableStateFlow<List<String>>(emptyList())
    val analysisPeriod = MutableStateFlow(7) // 7, 30, or 365 days

    init {
        val savedPlan = planRepo.getPlan()
        if (!savedPlan.isNullOrBlank()) {
            _uiState.value = PlanUIState.Success(savedPlan)
        }
        updateBehaviors()
    }

    fun buildPlan() {
        val aiProvider = planRepo.getAIProvider()
        val isModelDownloaded = localAI.isModelDownloaded() && planRepo.isModelCompleted()
        
        if (aiProvider == PlanRepository.AI_PROVIDER_LOCAL && !isModelDownloaded) {
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

            val prompt = constructPrompt()
            
            val result = try {
                if (aiProvider == PlanRepository.AI_PROVIDER_LOCAL) {
                    if (localAI.initialize()) {
                        localAI.generateResponse(prompt)
                    } else {
                        getApplication<Application>().getString(R.string.plan_error_init_local)
                    }
                } else {
                    val apiKey = planRepo.getApiKey()
                    val cloudProvider = planRepo.getCloudProvider()
                    val selectedModel = planRepo.getOpenRouterModel()
                    
                    if (apiKey.isBlank()) {
                        getApplication<Application>().getString(R.string.plan_error_no_api_key)
                    } else {
                        cloudAI.generateResponse(
                            provider = cloudProvider,
                            apiKey = apiKey,
                            prompt = prompt,
                            model = selectedModel
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

    fun updateBehaviors() {
        val type = goalType.value
        val rel = planRepo.getReligion().lowercase()
        
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
        if (selectedBehavior.value.isEmpty()) {
            selectedBehavior.value = list.firstOrNull() ?: ""
        }
    }

    private fun constructPrompt(): String {
        val days = analysisPeriod.value
        val startDate = LocalDate.now().minusDays(days.toLong())
        val logs = behaviorRepo.getLogsForRange(startDate, LocalDate.now())
        
        val behaviorSummary = if (logs.isEmpty()) getApplication<Application>().getString(R.string.chat_no_logs)
        else logs.joinToString("\n") { 
            "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
        }

        val typeText = if (goalType.value == "quit") getApplication<Application>().getString(R.string.plan_goal_quit) + ": " 
                       else getApplication<Application>().getString(R.string.plan_goal_commit) + ": "
        val targetBehavior = selectedBehavior.value
        val religion = planRepo.getReligion()
        val language = if (planRepo.getPlanLanguage() == "ar") "العربية" else "English"

        return """
            لغة الخطة المطلوبة: $language
            ديانة المستخدم: $religion
            الهدف الأساسي: $typeText $targetBehavior
            الفترة الزمنية للتحليل المقدمة: $days يوماً
            
            سجلات السلوك لهذه الفترة:
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

    override fun onCleared() {
        super.onCleared()
        localAI.close()
        generationJob?.cancel()
    }
}

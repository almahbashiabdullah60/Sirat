package com.atyafcode.sirat.features.planbuilder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.core.utils.planRepository
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.CloudAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
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

    val planLanguage = MutableStateFlow(planRepo.getPlanLanguage())
    val religion = MutableStateFlow(planRepo.getReligion())
    val aiProvider = MutableStateFlow(planRepo.getAIProvider())
    val apiKey = MutableStateFlow(planRepo.getApiKey())

    init {
        val savedPlan = planRepo.getPlan()
        if (!savedPlan.isNullOrBlank()) {
            _uiState.value = PlanUIState.Success(savedPlan)
        }
    }

    fun buildPlan() {
        viewModelScope.launch {
            _uiState.value = PlanUIState.Loading
            
            // Save current settings
            planRepo.setPlanLanguage(planLanguage.value)
            planRepo.setReligion(religion.value)
            planRepo.setAIProvider(aiProvider.value)
            planRepo.setApiKey(apiKey.value)

            val prompt = constructPrompt()
            
            val result = if (aiProvider.value == PlanRepository.AI_PROVIDER_LOCAL) {
                if (localAI.initialize()) {
                    localAI.generateResponse(prompt)
                } else {
                    "فشل في تهيئة الذكاء الاصطناعي المحلي. تأكد من تنزيل النموذج."
                }
            } else {
                cloudAI.generateResponse(apiKey.value, prompt)
            }

            if (result.contains("فشل")) {
                _uiState.value = PlanUIState.Error(result)
            } else {
                planRepo.savePlan(result)
                _uiState.value = PlanUIState.Success(result)
            }
        }
    }

    private fun constructPrompt(): String {
        val last30DaysLogs = behaviorRepo.getLogsForRange(LocalDate.now().minusDays(30), LocalDate.now())
        val behaviorSummary = last30DaysLogs.joinToString("\n") { 
            "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
        }

        return """
            لغة الخطة المطلوبة: ${if (planLanguage.value == "ar") "العربية" else "الإنجليزية"}
            ديانة المستخدم: ${religion.value}
            
            سجلات السلوك لآخر 30 يوم:
            $behaviorSummary
            
            بناءً على هذه البيانات، قم ببناء خطة تعافي مخصصة وعملية تتضمن:
            1. تحليل لأنماط السلوك ونقاط الضعف.
            2. خطوات عملية يومية للإقلاع عن هذا السلوك.
            3. نصائح روحية ودينية بناءً على ديانة المستخدم المذكورة أعلاه لتقوية الإرادة.
            4. بدائل صحية مقترحة للأوقات التي يزداد فيها السلوك.
            
            اجعل الخطة مشجعة، احترافية، وسهلة التنفيذ.
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        localAI.close()
    }
}

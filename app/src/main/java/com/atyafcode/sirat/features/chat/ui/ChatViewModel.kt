package com.atyafcode.sirat.features.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.core.utils.planRepository
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.CloudAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.OpenAIMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class ChatUIState {
    object Idle : ChatUIState()
    data class Loading(val message: String = "جاري التفكير...") : ChatUIState()
    data class Error(val message: String) : ChatUIState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val behaviorRepo = application.behaviorRepository()
    private val planRepo = application.planRepository()
    private val localAI = LocalAIProvider(application)
    private val cloudAI = CloudAIProvider()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _uiState = MutableStateFlow<ChatUIState>(ChatUIState.Idle)
    val uiState: StateFlow<ChatUIState> = _uiState

    init {
        // Initial welcome message from the AI doctor
        _messages.value = listOf(
            ChatMessage("أهلاً بك. أنا طبيب صراط الذكي، رفيقك في رحلة التعافي والانضباط. كيف يمكنني مساعدتك اليوم؟", false)
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text, true)
        _messages.value = _messages.value + userMsg
        
        viewModelScope.launch {
            _uiState.value = ChatUIState.Loading("جاري الاتصال بالطبيب الذكي...")
            
            val statusJob = launch {
                val loadingMessages = listOf(
                    "الطبيب يقرأ سجلاتك...",
                    "جاري تحليل المشكلة...",
                    "جاري استحضار النصيحة الروحية...",
                    "الطبيب يكتب الرد الآن..."
                )
                var index = 0
                while (true) {
                    delay(3000)
                    _uiState.value = ChatUIState.Loading(loadingMessages[index % loadingMessages.size])
                    index++
                }
            }
            
            try {
                val aiProvider = planRepo.getAIProvider()
                val apiKey = planRepo.getApiKey()
                val cloudProvider = planRepo.getCloudProvider()
                val selectedModel = planRepo.getOpenRouterModel()

                if (aiProvider == PlanRepository.AI_PROVIDER_LOCAL) {
                    if (localAI.initialize()) {
                        val context = constructBehaviorContext()
                        val history = _messages.value.takeLast(10).joinToString("\n") { 
                            if (it.isUser) "User: ${it.text}" else "Assistant: ${it.text}" 
                        }
                        
                        val fullContext = "$context\n\nRecent History:\n$history"
                        val response = localAI.generateChatResponse(fullContext, text)
                        
                        _messages.value = _messages.value + ChatMessage(response, false)
                        _uiState.value = ChatUIState.Idle
                    } else {
                        _uiState.value = ChatUIState.Error("فشل في تهيئة المحرك الذكي. تأكد من وجود مساحة كافية.")
                    }
                } else {
                    // Cloud Mode (OpenRouter)
                    if (apiKey.isBlank()) {
                        _uiState.value = ChatUIState.Error("يرجى إدخال API Key في تبويبة بناء الخطة")
                        return@launch
                    }

                    val context = constructBehaviorContext()
                    val cloudMessages = mutableListOf<OpenAIMessage>()
                    cloudMessages.add(OpenAIMessage("system", "You are Sirat AI Doctor. Professional, empathetic recovery coach. Use Arabic. Context: $context"))
                    
                    // Add last 5 messages for history
                    _messages.value.takeLast(6).forEach {
                        cloudMessages.add(OpenAIMessage(if (it.isUser) "user" else "assistant", it.text))
                    }

                    val response = cloudAI.generateChatResponse(
                        provider = cloudProvider,
                        apiKey = apiKey,
                        messages = cloudMessages,
                        model = selectedModel
                    )
                    
                    _messages.value = _messages.value + ChatMessage(response, false)
                    _uiState.value = ChatUIState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = ChatUIState.Error("خطأ: ${e.localizedMessage}")
            } finally {
                statusJob.cancel()
            }
        }
    }

    private fun constructBehaviorContext(): String {
        val last15DaysLogs = behaviorRepo.getLogsForRange(LocalDate.now().minusDays(15), LocalDate.now())
        val behaviorSummary = if (last15DaysLogs.isEmpty()) "لا توجد سجلات سلوك مسجلة حالياً." 
        else last15DaysLogs.joinToString("\n") { 
            "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
        }

        return "سجلات السلوك الأخيرة للمستخدم:\n$behaviorSummary"
    }

    override fun onCleared() {
        super.onCleared()
        localAI.close()
    }
}

package com.atyafcode.sirat.features.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
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
    object Loading : ChatUIState()
    data class Error(val message: String) : ChatUIState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val behaviorRepo = application.behaviorRepository()
    private val localAI = LocalAIProvider(application)

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
            _uiState.value = ChatUIState.Loading
            
            try {
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
            } catch (e: Exception) {
                _uiState.value = ChatUIState.Error("خطأ: ${e.localizedMessage}")
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

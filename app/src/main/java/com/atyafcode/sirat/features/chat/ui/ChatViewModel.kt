package com.atyafcode.sirat.features.chat.ui

import androidx.annotation.Keep
import com.atyafcode.sirat.R
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.core.utils.behaviorRepository
import com.atyafcode.sirat.core.utils.planRepository
import com.atyafcode.sirat.data.repository.PlanRepository
import com.atyafcode.sirat.features.planbuilder.domain.CloudAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.LocalAIProvider
import com.atyafcode.sirat.features.planbuilder.domain.OpenAIMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Keep
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class ChatUIState {
    object Idle : ChatUIState()
    data class Loading(val message: String) : ChatUIState()
    data class Error(val message: String) : ChatUIState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val behaviorRepo = application.behaviorRepository()
    private val planRepo = application.planRepository()
    private val localAI = LocalAIProvider(application)
    private val cloudAI = CloudAIProvider()
    private val gson = Gson()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _uiState = MutableStateFlow<ChatUIState>(ChatUIState.Idle)
    val uiState: StateFlow<ChatUIState> = _uiState

    private val _allowPlanAccess = MutableStateFlow(false)
    val allowPlanAccess: StateFlow<Boolean> = _allowPlanAccess

    private val _allowBehaviorAccess = MutableStateFlow(false)
    val allowBehaviorAccess: StateFlow<Boolean> = _allowBehaviorAccess

    init {
        loadChatHistory()
    }

    private fun loadChatHistory() {
        val savedHistory = planRepo.getChatHistory()
        if (savedHistory != null) {
            try {
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                val history: List<ChatMessage> = gson.fromJson(savedHistory, type)
                _messages.value = history
            } catch (e: Exception) {
                e.printStackTrace()
                setDefaultWelcome()
            }
        } else {
            setDefaultWelcome()
        }
    }

    private fun setDefaultWelcome() {
        _messages.value = listOf(
            ChatMessage(getApplication<Application>().getString(R.string.chat_welcome_message), false)
        )
    }

    private fun saveChatHistory() {
        val historyJson = gson.toJson(_messages.value)
        planRepo.saveChatHistory(historyJson)
    }

    fun setAllowPlanAccess(allow: Boolean) {
        _allowPlanAccess.value = allow
    }

    fun setAllowBehaviorAccess(allow: Boolean) {
        _allowBehaviorAccess.value = allow
    }

    fun clearChat() {
        _messages.value = listOf(
            ChatMessage(getApplication<Application>().getString(R.string.chat_welcome_message), false)
        )
        _uiState.value = ChatUIState.Idle
        planRepo.clearChatHistory()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text, true)
        _messages.value = _messages.value + userMsg
        saveChatHistory()
        
        viewModelScope.launch {
            _uiState.value = ChatUIState.Loading(getApplication<Application>().getString(R.string.chat_status_connecting))
            
            val statusJob = launch {
                val loadingMessages = listOf(
                    getApplication<Application>().getString(R.string.chat_status_reading_logs),
                    getApplication<Application>().getString(R.string.chat_status_analyzing),
                    getApplication<Application>().getString(R.string.chat_status_spiritual),
                    getApplication<Application>().getString(R.string.chat_status_writing)
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
                        val behaviorContext = constructBehaviorContext()
                        val history = _messages.value.takeLast(10).joinToString("\n") { 
                            if (it.isUser) "User: ${it.text}" else "Assistant: ${it.text}" 
                        }
                        
                        val fullContext = "$behaviorContext\n\nRecent History:\n$history"
                        val response = localAI.generateChatResponse(fullContext, text)
                        
                        _messages.value = _messages.value + ChatMessage(response, false)
                        saveChatHistory()
                        _uiState.value = ChatUIState.Idle
                    } else {
                        _uiState.value = ChatUIState.Error(getApplication<Application>().getString(R.string.chat_error_init_failed))
                    }
                } else {
                    // Cloud Mode (OpenRouter)
                    if (apiKey.isBlank()) {
                        _uiState.value = ChatUIState.Error(getApplication<Application>().getString(R.string.chat_error_no_api_key))
                        return@launch
                    }

                    val behaviorContext = constructBehaviorContext()
                    val cloudMessages = mutableListOf<OpenAIMessage>()
                    cloudMessages.add(OpenAIMessage("system", "You are Sirat AI Assistant. Professional, empathetic recovery coach. Use Arabic. Context: $behaviorContext"))
                    
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
                    saveChatHistory()
                    _uiState.value = ChatUIState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = ChatUIState.Error(getApplication<Application>().getString(R.string.plan_error_unexpected, e.localizedMessage ?: ""))
            } finally {
                statusJob.cancel()
            }
        }
    }

    private fun constructBehaviorContext(): String {
        val religion = planRepo.getReligion()
        val language = if (planRepo.getPlanLanguage() == "ar") "العربية" else "English"

        val planContent = if (_allowPlanAccess.value) {
            val plan = planRepo.getPlan()
            if (plan != null) "\n\nUser Recovery Plan:\n$plan" else "\n\n(No recovery plan found)"
        } else ""

        val behaviorSummary = if (_allowBehaviorAccess.value) {
            val allLogs = behaviorRepo.getAllLogs().take(30) // Take last 30 entries for context
            if (allLogs.isEmpty()) "\n\n" + getApplication<Application>().getString(R.string.chat_no_logs)
            else "\n\nFull Behavior Analysis/Logs (Last 30 entries):\n" + allLogs.joinToString("\n") {
                "التاريخ: ${it.date}, التكرار: ${it.count}, السبب: ${it.reason}"
            }
        } else ""

        return """
            User Religion: $religion
            Preferred Language: $language
            $planContent
            $behaviorSummary
            
            Instructions: Analyze the behavior and the plan (if provided) and talk to the user based on these settings.
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        localAI.close()
    }
}

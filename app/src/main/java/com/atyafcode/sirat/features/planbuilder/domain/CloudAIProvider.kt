package com.atyafcode.sirat.features.planbuilder.domain

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAICompatibleService {
    @POST("chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class ChatRequest(
    val model: String = "gpt-4o",
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val message: ChatMessage
)

class CloudAIProvider {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/v1/") // Can be dynamic based on provider
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(OpenAICompatibleService::class.java)

    suspend fun generateResponse(apiKey: String, prompt: String): String {
        return try {
            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = "أنت مستشار خبير في علاج الإدمان السلوكي."),
                    ChatMessage(role = "user", content = prompt)
                )
            )
            val response = service.getCompletion("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "رد فارغ من السحابة"
        } catch (e: Exception) {
            e.printStackTrace()
            "فشل في بناء الخطة سحابياً: ${e.localizedMessage}"
        }
    }
}

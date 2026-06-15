package com.atyafcode.sirat.features.planbuilder.domain

import com.atyafcode.sirat.data.repository.PlanRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// --- Gemini Models ---
interface GeminiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(@Query("key") apiKey: String, @Body request: GeminiRequest): GeminiResponse
}
data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String)
data class GeminiResponse(val candidates: List<GeminiCandidate>)
data class GeminiCandidate(val content: GeminiContent)

// --- OpenAI Models ---
interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(@Header("Authorization") authHeader: String, @Body request: OpenAIRequest): OpenAIResponse
}
data class OpenAIRequest(val model: String = "gpt-4o-mini", val messages: List<OpenAIMessage>)
data class OpenAIMessage(val role: String, val content: String)
data class OpenAIResponse(val choices: List<OpenAIChoice>)
data class OpenAIChoice(val message: OpenAIMessage)

class CloudAIProvider {

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun generateResponse(provider: String, apiKey: String, prompt: String): String {
        return try {
            when (provider) {
                PlanRepository.CLOUD_PROVIDER_GEMINI -> {
                    val service = createRetrofit("https://generativelanguage.googleapis.com/").create(GeminiService::class.java)
                    val request = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))))
                    val response = service.generateContent(apiKey, request)
                    response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "رد فارغ من Gemini"
                }
                PlanRepository.CLOUD_PROVIDER_OPENAI -> {
                    val service = createRetrofit("https://api.openai.com/").create(OpenAIService::class.java)
                    val request = OpenAIRequest(messages = listOf(OpenAIMessage("user", prompt)))
                    val response = service.getCompletion("Bearer $apiKey", request)
                    response.choices.firstOrNull()?.message?.content ?: "رد فارغ من OpenAI"
                }
                else -> "مزود غير مدعوم"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "فشل في بناء الخطة سحابياً: ${e.localizedMessage}"
        }
    }
}

package com.atyafcode.sirat.features.planbuilder.domain

import com.atyafcode.sirat.data.repository.PlanRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
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

// --- OpenRouter Models ---
interface OpenRouterService {
    @GET("api/v1/models")
    suspend fun getModels(@Header("Authorization") authHeader: String): OpenRouterModelsResponse

    @POST("api/v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: OpenRouterChatRequest
    ): OpenRouterChatResponse
}

data class OpenRouterModelsResponse(val data: List<OpenRouterModel>)
data class OpenRouterModel(val id: String, val name: String, val pricing: OpenRouterPricing)
data class OpenRouterPricing(val prompt: String, val completion: String)

data class OpenRouterChatRequest(val model: String, val messages: List<OpenAIMessage>)
data class OpenRouterChatResponse(val choices: List<OpenRouterChoice>)
data class OpenRouterChoice(val message: OpenAIMessage)

class CloudAIProvider {

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun fetchOpenRouterModels(apiKey: String): List<OpenRouterModel> {
        return try {
            val service = createRetrofit("https://openrouter.ai/").create(OpenRouterService::class.java)
            val response = service.getModels("Bearer $apiKey")
            // Filter for free models or return all as requested
            response.data
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun generateResponse(
        provider: String, 
        apiKey: String, 
        prompt: String, 
        model: String? = null
    ): String {
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
                PlanRepository.CLOUD_PROVIDER_OPENROUTER -> {
                    val service = createRetrofit("https://openrouter.ai/").create(OpenRouterService::class.java)
                    val request = OpenRouterChatRequest(
                        model = model ?: "google/gemini-2.0-flash-exp:free",
                        messages = listOf(OpenAIMessage("user", prompt))
                    )
                    val response = service.getChatCompletion("Bearer $apiKey", request)
                    response.choices.firstOrNull()?.message?.content ?: "رد فارغ من OpenRouter"
                }
                else -> "مزود غير مدعوم"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "فشل في الاتصال بالسحاب: ${e.localizedMessage}"
        }
    }

    suspend fun generateChatResponse(
        provider: String,
        apiKey: String,
        messages: List<OpenAIMessage>,
        model: String? = null
    ): String {
        return try {
            if (provider == PlanRepository.CLOUD_PROVIDER_OPENROUTER) {
                val service = createRetrofit("https://openrouter.ai/").create(OpenRouterService::class.java)
                val request = OpenRouterChatRequest(
                    model = model ?: "google/gemini-2.0-flash-exp:free",
                    messages = messages
                )
                val response = service.getChatCompletion("Bearer $apiKey", request)
                response.choices.firstOrNull()?.message?.content ?: "رد فارغ من OpenRouter"
            } else {
                "التحدث المتعدد الأدوار مدعوم حالياً فقط عبر OpenRouter"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ في الشات السحابي: ${e.localizedMessage}"
        }
    }
}

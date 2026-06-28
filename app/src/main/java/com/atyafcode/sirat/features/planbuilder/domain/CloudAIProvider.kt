package com.atyafcode.sirat.features.planbuilder.domain

import androidx.annotation.Keep
import com.atyafcode.sirat.data.repository.PlanRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Models ---
interface GeminiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(@Query("key") apiKey: String, @Body request: GeminiRequest): GeminiResponse
}
@Keep
data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiConfig? = GeminiConfig())
@Keep
data class GeminiConfig(val maxOutputTokens: Int = 1024, val temperature: Double = 0.5)
@Keep
data class GeminiContent(val parts: List<GeminiPart>)
@Keep
data class GeminiPart(val text: String)
@Keep
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
@Keep
data class GeminiCandidate(val content: GeminiContent?)

// --- OpenAI Models ---
interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(@Header("Authorization") authHeader: String, @Body request: OpenAIRequest): OpenAIResponse
}
@Keep
data class OpenAIRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<OpenAIMessage>,
    val max_tokens: Int = 1024
)
@Keep
data class OpenAIMessage(val role: String, val content: String)
@Keep
data class OpenAIResponse(val choices: List<OpenAIChoice>?)
@Keep
data class OpenAIChoice(val message: OpenAIMessage?)

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

@Keep
data class OpenRouterModelsResponse(val data: List<OpenRouterModel>?)
@Keep
data class OpenRouterModel(val id: String, val name: String, val pricing: OpenRouterPricing?)
@Keep
data class OpenRouterPricing(val prompt: String, val completion: String)

@Keep
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val max_tokens: Int = 1024
)
@Keep
data class OpenRouterChatResponse(val choices: List<OpenRouterChoice>?)
@Keep
data class OpenRouterChoice(val message: OpenAIMessage?)

class CloudAIProvider {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun fetchOpenRouterModels(apiKey: String): List<OpenRouterModel> {
        return try {
            val service = createRetrofit("https://openrouter.ai/").create(OpenRouterService::class.java)
            val response = service.getModels("Bearer $apiKey")
            val allModels = response.data ?: emptyList()
            
            // فلترة الموديلات المجانية فقط (التي سعرها 0)
            allModels.filter { model ->
                model.pricing?.prompt == "0" && model.pricing.completion == "0"
            }
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
            android.util.Log.d("SiratAI", "Sending request to $provider with model: $model")
            val responseText = when (provider) {
                PlanRepository.CLOUD_PROVIDER_GEMINI -> {
                    val service = createRetrofit("https://generativelanguage.googleapis.com/").create(GeminiService::class.java)
                    val request = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))))
                    val response = service.generateContent(apiKey, request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "رد فارغ من Gemini (قد يكون بسبب خطأ في الـ API Key)"
                }
                PlanRepository.CLOUD_PROVIDER_OPENAI -> {
                    val service = createRetrofit("https://api.openai.com/").create(OpenAIService::class.java)
                    val request = OpenAIRequest(messages = listOf(OpenAIMessage("user", prompt)))
                    val response = service.getCompletion("Bearer $apiKey", request)
                    response.choices?.firstOrNull()?.message?.content ?: "رد فارغ من OpenAI (تأكد من الرصيد والـ Key)"
                }
                PlanRepository.CLOUD_PROVIDER_OPENROUTER -> {
                    val service = createRetrofit("https://openrouter.ai/").create(OpenRouterService::class.java)
                    val request = OpenRouterChatRequest(
                        model = model ?: "google/gemini-2.0-flash-exp:free",
                        messages = listOf(OpenAIMessage("user", prompt))
                    )
                    val response = service.getChatCompletion("Bearer $apiKey", request)
                    response.choices?.firstOrNull()?.message?.content ?: "رد فارغ من OpenRouter (تأكد من الموديل المختار وصلاحية الـ Key)"
                }
                else -> "مزود غير مدعوم"
            }
            android.util.Log.d("SiratAI", "Response received: ${responseText.take(100)}...")
            responseText
        } catch (e: Exception) {
            android.util.Log.e("SiratAI", "Error in generateResponse", e)
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
                response.choices?.firstOrNull()?.message?.content ?: "رد فارغ من OpenRouter"
            } else {
                "التحدث المتعدد الأدوار مدعوم حالياً فقط عبر OpenRouter"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ في الشات السحابي: ${e.localizedMessage}"
        }
    }
}

package com.atyafcode.sirat.features.planbuilder.domain

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class LocalAIProvider(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val modelFileName = "gemma-2b-it-cpu-int4.bin"
    private val modelFile = File(context.filesDir, modelFileName)
    
    // Example URL for a compatible model (Gemma 2B)
    private val modelUrl = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"

    fun isModelDownloaded(): Boolean = modelFile.exists()

    fun downloadModel(): Long {
        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("تنزيل محرك الذكاء الاصطناعي")
            .setDescription("يتم تنزيل النموذج لمرة واحدة لضمان خصوصيتك")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, modelFileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (!isModelDownloaded()) return@withContext false
        
        try {
            if (llmInference == null) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(1024)
                    .setTopK(40)
                    .setTemperature(0.7f)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            llmInference?.generateResponse(prompt) ?: "حدث خطأ في محرك الذكاء الاصطناعي"
        } catch (e: Exception) {
            e.printStackTrace()
            "فشل في بناء الخطة محلياً: ${e.localizedMessage}"
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}

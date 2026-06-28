package com.atyafcode.sirat.features.planbuilder.domain

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadStatus(
    val id: Long = -1L,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val reason: String? = null
) {
    val downloadedMB: String get() = String.format("%.1f", bytesDownloaded / (1024f * 1024f))
    val totalMB: String get() = String.format("%.1f", totalBytes / (1024f * 1024f))
    val remainingMB: String get() = String.format("%.1f", (totalBytes - bytesDownloaded) / (1024f * 1024f))
}

class LocalAIProvider(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val modelMutex = Mutex()
    private val modelFileName = "gemma-2b-it-cpu-int4.bin"
    
    // Use ExternalFilesDir so DownloadManager can write to it. 
    // This path is still private to the app and deleted upon uninstallation.
    private val modelFile: File get() = File(context.getExternalFilesDir(null), modelFileName)
    
    // Public Hugging Face mirror — no authentication required
    private val modelUrl = "https://huggingface.co/metsman/gemma-2b-it-cpu-int4-org/resolve/main/gemma-2b-it-cpu-int4.bin?download=true"

    fun isModelDownloaded(): Boolean {
        // Gemma 2B int4 is around 1.35GB
        return modelFile.exists() && modelFile.length() > 1_100_000_000
    }

    fun downloadModel(): Long {
        try {
            // If model already exists and is large enough, don't download again
            if (isModelDownloaded()) {
                Toast.makeText(context, "المحرك موجود بالفعل", Toast.LENGTH_SHORT).show()
                return -2L
            }

            // Delete partial or existing small file to prevent DownloadManager conflict
            if (modelFile.exists()) {
                modelFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(modelUrl))
                .setTitle("تنزيل محرك صراط الذكي")
                .setDescription("جاري تجهيز بيئة الذكاء الاصطناعي الخاصة بك...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, modelFileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = downloadManager.enqueue(request)
            Toast.makeText(context, "بدأ التنزيل.. يمكنك متابعة التقدم في الإشعارات", Toast.LENGTH_LONG).show()
            return id
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل بدء التنزيل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    fun cancelDownload(downloadId: Long) {
        if (downloadId < 0) return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
    }

    fun getDownloadStatus(downloadId: Long): DownloadStatus {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        android.util.Log.d("SiratAI", "Checking status for ID: $downloadId")
        
        // If no ID is provided, try to find an active download by title/name
        var idToQuery = downloadId
        if (idToQuery < 0) {
            android.util.Log.d("SiratAI", "No ID provided, scanning all downloads...")
            val queryAll = DownloadManager.Query()
            val allCursor = downloadManager.query(queryAll)
            if (allCursor != null) {
                while (allCursor.moveToNext()) {
                    val title = allCursor.getString(allCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    val status = allCursor.getInt(allCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    android.util.Log.d("SiratAI", "Found download: $title, Status: $status")
                    
                    if (title == "تنزيل محرك صراط الذكي") {
                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                            idToQuery = allCursor.getLong(allCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                            android.util.Log.d("SiratAI", "Matched active download! New ID: $idToQuery")
                            break
                        }
                    }
                }
                allCursor.close()
            }
        }

        if (idToQuery < 0) {
            android.util.Log.d("SiratAI", "No active download found.")
            return DownloadStatus()
        }

        val query = DownloadManager.Query().setFilterById(idToQuery)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            
            android.util.Log.d("SiratAI", "Status for ID $id: $status, Progress: $bytesDownloaded/$totalBytes")
            
            val progress = if (totalBytes > 0) (bytesDownloaded * 100L / totalBytes).toInt() else 0
            
            cursor.close()

            return when (status) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    DownloadStatus(
                        id = id,
                        progress = progress, 
                        bytesDownloaded = bytesDownloaded, 
                        totalBytes = totalBytes, 
                        isRunning = true,
                        isPaused = false
                    )
                }
                DownloadManager.STATUS_PAUSED -> {
                    DownloadStatus(
                        id = id,
                        progress = progress,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        isRunning = true,
                        isPaused = true
                    )
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    android.util.Log.d("SiratAI", "Download SUCCESSFUL for ID $id")
                    DownloadStatus(id = id, progress = 100, isCompleted = true)
                }
                DownloadManager.STATUS_FAILED -> {
                    android.util.Log.e("SiratAI", "Download FAILED for ID $id, Reason: $reason")
                    val errorMsg = when (reason) {
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "مساحة التخزين غير كافية"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "وسيلة التخزين غير موجودة"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "خطأ في بيانات الشبكة (HTTP)"
                        DownloadManager.ERROR_FILE_ERROR -> "خطأ في ملفات النظام"
                        401 -> "الرابط يتطلب مصادقة (401 Unauthorized)"
                        403 -> "الوصول مرفوض من الخادم (403 Forbidden)"
                        1008 -> "رابط التحميل غير صالح أو يتطلب صلاحيات"
                        else -> "خطأ غير معروف (رمز السبب: $reason)"
                    }
                    DownloadStatus(id = id, isFailed = true, reason = errorMsg)
                }
                else -> DownloadStatus(id = id)
            }
        }
        android.util.Log.w("SiratAI", "Cursor was empty for ID $idToQuery")
        cursor?.close()
        return DownloadStatus()
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (!isModelDownloaded()) {
            android.util.Log.e("SiratAI", "Model file missing or incomplete during initialization")
            return@withContext false
        }
        
        modelMutex.withLock {
            try {
                if (llmInference == null) {
                    android.util.Log.d("SiratAI", "Initializing LlmInference with model: ${modelFile.absolutePath}")
                    
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    
                    llmInference = LlmInference.createFromOptions(context, options)
                    android.util.Log.d("SiratAI", "LlmInference initialized successfully")
                }
                true
            } catch (e: Exception) {
                android.util.Log.e("SiratAI", "CRITICAL: Failed to create LlmInference", e)
                false
            } catch (e: Error) {
                android.util.Log.e("SiratAI", "NATIVE ERROR: Critical failure in MediaPipe Engine", e)
                false
            }
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            try {
                val engine = llmInference
                if (engine == null) {
                    return@withLock "محرك الذكاء الاصطناعي غير جاهز"
                }
                
                android.util.Log.d("SiratAI", "Generating response for prompt length: ${prompt.length}")
                val response = engine.generateResponse(prompt)
                android.util.Log.d("SiratAI", "Response generated successfully")
                response ?: "خطأ: لم يتم توليد رد من المحرك المحلي."
            } catch (e: Exception) {
                android.util.Log.e("SiratAI", "Error during generation", e)
                "فشل في بناء الخطة محلياً: ${e.localizedMessage}"
            } catch (e: Error) {
                android.util.Log.e("SiratAI", "Native crash during generation", e)
                "حدث خطأ فني في محرك الجهاز. حاول إعادة تشغيل التطبيق."
            }
        }
    }

    /**
     * Generates a chat response. MediaPipe's LlmInference doesn't have a built-in stateful chat API 
     * in the basic GenAI Tasks yet (it's primarily single-shot), so we simulate chat by appending 
     * the history to the prompt.
     */
    suspend fun generateChatResponse(history: String, userMessage: String): String {
        // Significantly simplified prompt for Gemma 2B to prevent hallucinations
        val systemPrompt = """
            Persona: Support Coach.
            Goal: Recovery advice. 
            Language: Arabic.
            Constraint: Keep it short (max 2 sentences).
            Context: $history
            User: $userMessage
            Assistant:
        """.trimIndent()
        
        val response = generateResponse(systemPrompt)
        return if (response.isBlank() || response.length < 5) "المحرك المحلي يواجه صعوبة حالياً. يرجى تفعيل المحرك السحابي (OpenRouter) للحصول على جودة أفضل." else response
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}

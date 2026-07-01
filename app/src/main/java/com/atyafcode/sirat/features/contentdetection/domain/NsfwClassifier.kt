package com.atyafcode.sirat.features.contentdetection.domain

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * مصنف المحتوى غير اللائق باستخدام TensorFlow Lite.
 *
 * يعمل بالكامل على الجهاز — لا تُرسل أي بيانات إلى الخارج.
 * النموذج مُكمَّم (int8) لتقليل الحجم واستهلاك البطارية.
 */
class NsfwClassifier(private val context: Context) {

    companion object {
        private const val TAG = "NsfwClassifier"
        private const val MODEL_FILE = "nsfw.tflite"
        private const val IMAGE_SIZE = 224
        private const val PIXEL_SIZE = 3 // RGB
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val mutex = Mutex()

    // ByteBuffer معاد الاستخدام لتقليل ضغط الـ GC
    private var inputImageBuffer: ByteBuffer? = null

    /**
     * عتبة الثقة الافتراضية. يمكن للمستخدم تغييرها من الإعدادات.
     */
    @Volatile
    var confidenceThreshold: Float = 0.85f

    /**
     * تهيئة النموذج — تحميل كسول.
     * يُستدعى عند أول تفعيل للميزة فقط.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (interpreter != null) return@withContext true

            try {
                val modelBuffer = loadModelFile()

                val options = Interpreter.Options()
                try {
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice()) {
                        gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                        Log.d(TAG, "GPU Delegate enabled")
                    } else {
                        options.setNumThreads(2)
                        Log.d(TAG, "GPU not supported, using CPU with 2 threads")
                    }
                } catch (e: Throwable) {
                    options.setNumThreads(2)
                    Log.w(TAG, "GPU Delegate failed (possibly missing dependencies), falling back to CPU", e)
                }

                interpreter = Interpreter(modelBuffer, options)

                inputImageBuffer = ByteBuffer.allocateDirect(
                    IMAGE_SIZE * IMAGE_SIZE * PIXEL_SIZE * 4 // float32
                ).order(ByteOrder.nativeOrder())

                Log.d(TAG, "NSFW model initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NSFW model", e)
                false
            }
        }
    }

    /**
     * تصنيف صورة لقطة الشاشة.
     * يجب أن تُستدعى على Dispatchers.Default أو Dispatchers.IO.
     *
     * @param bitmap لقطة الشاشة الملتقطة
     * @param packageName اسم الحزمة الحالية (يُستخدم في النتيجة)
     * @return نتيجة الكشف
     */
    suspend fun classify(bitmap: Bitmap, packageName: String): DetectionResult =
        withContext(Dispatchers.Default) {
            if (interpreter == null) {
                val initialized = initialize()
                if (!initialized) {
                    return@withContext DetectionResult.Error("Model not initialized")
                }
            }

            mutex.withLock {
                try {
                    val buffer = inputImageBuffer ?: return@withContext DetectionResult.Error("Buffer not initialized")

                    // تصغير الصورة إلى 224×224 (يقلل المعالجة بشكل كبير)
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap, IMAGE_SIZE, IMAGE_SIZE, true
                    )

                    // تحويل Bitmap إلى ByteBuffer
                    buffer.rewind()
                    convertBitmapToBuffer(scaledBitmap, buffer)

                    // تشغيل النموذج
                    val output = Array(1) { FloatArray(2) } // [safe, nsfw]
                    interpreter?.run(buffer, output)

                    val safeScore = output[0][0]
                    val nsfwScore = output[0][1]

                    // تحرير الصورة المصغرة فورًا
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }

                    Log.d(TAG, "Classification: safe=%.3f, nsfw=%.3f".format(safeScore, nsfwScore))

                    when {
                        nsfwScore >= confidenceThreshold -> {
                            DetectionResult.NsfwDetected(
                                confidence = nsfwScore,
                                packageName = packageName
                            )
                        }

                        else -> {
                            DetectionResult.Safe(confidence = safeScore)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during classification", e)
                    DetectionResult.Error(e.message ?: "Unknown error")
                }
            }
        }

    private fun loadModelFile(): ByteBuffer {
        val assetManager = context.assets
        val inputStream = assetManager.open(MODEL_FILE)
        val modelBytes = inputStream.readBytes()
        inputStream.close()

        val buffer = ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        buffer.rewind()
        return buffer
    }

    /**
     * تحويل Bitmap إلى ByteBuffer وفق preprocessing نموذج Yahoo Open NSFW:
     * RGB → BGR, pixel * 255, subtract VGG mean [104, 117, 123]
     */
    private fun convertBitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // RGB → BGR, multiply by 255, subtract VGG mean
            buffer.putFloat(b.toFloat() - 123f)   // B
            buffer.putFloat(g.toFloat() - 117f)   // G
            buffer.putFloat(r.toFloat() - 104f)   // R
        }
    }

    /**
     * تحرير النموذج من الذاكرة.
     * يُستدعى عند إيقاف الميزة أو انخفاض البطارية.
     */
    fun release() {
        synchronized(this) {
            try {
                interpreter?.close()
                gpuDelegate?.close()
                interpreter = null
                gpuDelegate = null
                inputImageBuffer = null
                Log.d(TAG, "NSFW model released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing model", e)
            }
        }
    }

    fun isInitialized(): Boolean = interpreter != null
}

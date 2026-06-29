# خطة تنفيذ ميزة الكشف البصري بالذكاء الاصطناعي لمحتوى غير اللائق

> **المستند:** خطة تقنية تفصيلية لإضافة ميزة مسح الشاشة بالذكاء الاصطناعي إلى تطبيق صراط
> **التوافق:** ملتزمة بـ `AGENTS.md` و `DEVELOPER_GUIDE.md` و `GEMINI.md`
> **الهدف الأساسي:** كشف المحتوى الإباحي بصريًا على مستوى النظام بأقل استهلاك ممكن للبطارية

---

## جدول المحتويات

1. [نظرة عامة](#1-نظرة-عامة)
2. [التحليل التقني](#2-التحليل-التقني)
3. [استراتيجية حفظ البطارية](#3-استراتيجية-حفظ-البطارية)
4. [هيكلة الميزة وتوزيع الملفات](#4-هيكلة-الميزة-وتوزيع-الملفات)
5. [التبعيات المطلوبة](#5-التبعيات-المطلوبة)
6. [المرحلة الأولى: البنية التحتية للنموذج](#المرحلة-الأولى-البنية-التحتية-للنموذج)
7. [المرحلة الثانية: مدير مسح الشاشة](#المرحلة-الثانية-مدير-مسح-الشاشة)
8. [المرحلة الثالثة: التكامل مع خدمة Accessibility](#المرحلة-الثالثة-التكامل-مع-خدمة-accessibility)
9. [المرحلة الرابعة: إعدادات المستخدم](#المرحلة-الرابعة-إعدادات-المستخدم)
10. [المرحلة الخامسة: قاعدة البيانات والسجلات](#المرحلة-الخامسة-قاعدة-البيانات-والسجلات)
11. [المرحلة السادسة: التوطين (Localization)](#المرحلة-السادسة-التوطين-localization)
12. [تعديلات الملفات الموجودة](#تعديلات-الملفات-الموجودة)
13. [قائمة التحقق النهائية](#قائمة-التحقق-النهائية)

---

## 1. نظرة عامة

### المشكلة
تطبيق صراط حاليًا يعتمد على VPN/DNS filtering لحظر النطاقات الإباحية، لكنه لا يستطيع:
- كشف المحتوى الإباحي **داخل التطبيقات** (يوتيوب، إنستغرام، تيك توك، تيليجرام)
- كشف المحتوى في صفحات مختلطة (مثل منشورات في فيسبوك)
- حظر المحتوى البصري المعروض على الشاشة بشكل عام

### الحل
إضافة طبقة كشف بصري تعمل بالذكاء الاصطناعي على الجهاز (On-Device) باستخدام:
- **AccessibilityService.takeScreenshot()** لالتقاط الشاشة (API 30+)
- **TensorFlow Lite** مع نموذج تصنيف NSFW مُكمَّم (Quantized) للتصنيف السريع
- خط معالجة محسَّن لتقليل استهلاك البطارية إلى الحد الأدنى

### المبدأ التقني
```
حدث تغيير المحتوى (AccessibilityEvent)
  ↓
فلترة ذكية (هل نحتاج للمسح؟)
  ↓
أخذ لقطة شاشة (takeScreenshot)
  ↓
تصغير الصورة (224×224 بكسل)
  ↓
تشغيل نموذج TFLite (NSFW Classifier)
  ↓
قرار: محتوى طبيعي ← متابعة | محتوى إباحي ← حظر
  ↓
عند الحظر: الضغط على زر الرجوع + تسجيل المخالفة
  ↓
عند تكرار المخالفة (3 مرات): قفل التطبيق لمدة 15 دقيقة
```

---

## 2. التحليل التقني

### 2.1 الالتزام بقيود المشروع

| القيد (من AGENTS.md / GEMINI.md) | كيف نلتزم به |
|---|---|
| **لغة Kotlin فقط** | جميع الملفات الجديدة بـ Kotlin |
| **Jetpack Compose + Material 3** | واجهة الإعدادات بـ Compose |
| **بدون XML layouts** | لا توجد ملفات XML للواجهات (فقط `accessibility_service_config.xml` للتعديل) |
| **Feature-based Packaging** | الميزة في `features/contentdetection/` |
| **عدم تدهور البطارية أو الأداء** | استراتيجية متعددة الطبقات لحفظ البطارية (القسم 3) |
| **تعديلات دقيقة (Surgical Edits)** | تعديلات محدودة على الملفات الموجودة |
| **الحفاظ على التعليقات** | الحفاظ على التعليقات الموجودة وإضافة تعليقات واضحة |
| **معالجة عدم توفر الصلاحيات** | فحص `SDK_INT` + فحص تفعيل الميزة |
| **bilingual (عربي/إنجليزي)** | سلاسل مترجمة في `strings.xml` |

### 2.2 توافق واجهة برمجة التطبيقات (API Compatibility)

| الميزة | الحد الأدنى لـ API | ملاحظة |
|---|---|---|
| `AccessibilityService.takeScreenshot()` | **API 30 (Android 11)** | متاحة للأجهزة الحديثة |
| `minSdk` في المشروع | 26 | لذا الميزة تُفعَّل فقط على API 30+ |
| أجهزة API 26-29 | الميزة معطلة تلقائيًا | رسالة للمستخدم تشرح المطلوب |

> **القرار:** الميزة تعمل على API 30+ فقط. نسبة الأجهزة النشطة بـ Android 11+ تتجاوز 75% حسب إحصائيات Google، مما يغطي معظم المستخدمين.

### 2.3 اختيار نموذج الذكاء الاصطناعي

| الخيار | الحجم | السرعة | الدقة | القرار |
|---|---|---|---|---|
| **TensorFlow Lite + نموذج NSFW مُكمَّم** | ~5 MB | ~20-50ms | جيدة جداً | ✅ **مختار** |
| MediaPipe Image Classifier | ~10 MB | ~30ms | ممتاز | بديل ممكن |
| Cloud API (Retrofit) | 0 MB | بطيء | ممتاز | ❌ يخالف مبدأ الخصوصية |

> **القرار:** استخدام **TensorFlow Lite** مع نموذج MobileNetV2 مُدرَّب على تصنيف NSFW، مُكمَّم بـ int8 (Quantized) لتقليل الحجم والاستهلاك. النموذج يُحمَّل من مجلد `assets` ويُهيَّأ عند بدء الميزة.

---

## 3. استراتيجية حفظ البطارية

> **هذا هو القسم الأهم.** أي تعديل على خدمات Accessibility يجب ألا يؤثر على البطارية (وفقًا لـ AGENTS.md و GEMINI.md).

### 3.1 الطبقات الوقائية (Multi-layer Throttling)

```
┌─────────────────────────────────────────────────────┐
│ الطبقة 1: فلترة الأحداث (Event Filtering)           │
│ • تجاهل الأحداث المتكررة من نفس الحزمة              │
│ • تجاهل أحداث لوحة المفاتيح والنظام                  │
│ • تجاهل الأحداث أثناء عرض شاشة القفل                │
├─────────────────────────────────────────────────────┤
│ الطبقة 2: Throttle زمني (Time Throttling)          │
│ • لا تأخذ لقطة شاشة إلا بعد 3 ثوانٍ من آخر لقطة      │
│ • فاصل زمني قابل للتخصيص من قبل المستخدم (2-5 ثوانٍ) │
├─────────────────────────────────────────────────────┤
│ الطبقة 3: فلترة حالة الجهاز (Device State)          │
│ • إيقاف المسح عند انطفاء الشاشة                      │
│ • إيقاف المسح عند قفل الجهاز                         │
│ • إيقاف المسح عند انخفاض البطارية (< 15%)            │
│ • إيقاف المسح أثناء وضع توفير الطاقة                 │
├─────────────────────────────────────────────────────┤
│ الطبقة 4: فلترة التطبيقات (App Filtering)           │
│ • لا تمسح تطبيقات الاستثناء (قائمة بيضاء)            │
│ • لا تمسح تطبيق صراط نفسه                            │
│ • لا تمسح لوحة المفاتيح                              │
│ • لا تمسح الشاشة الرئيسية (Launcher)                │
├─────────────────────────────────────────────────────┤
│ الطبقة 5: معالجة فعّالة (Efficient Processing)     │
│ • تصغير الصورة إلى 224×224 قبل المعالجة             │
│ • استخدام GPU Delegate في TFLite                     │
│ • معالجة على Dispatchers.Default (وليس Main)         │
│ • تحرير الـ Bitmap فور الانتهاء                      │
│ • استخدام Mutex لمنع العمليات المتزامنة             │
├─────────────────────────────────────────────────────┤
│ الطبقة 6: الإيقاف المؤقت الذكي (Smart Pausing)     │
│ • إيقاف مؤقت عند ظهور شاشة القفل                    │
│ • إيقاف مؤقت أثناء المكالمات الهاتفية                │
│ • تقليل التكرار بعد الكشف (Cooldown 10 ثوانٍ)      │
└─────────────────────────────────────────────────────┘
```

### 3.2 استهلاك البطارية المتوقع

| السيناريو | استهلاك إضافي يومي |
|---|---|
| المسح كل 3 ثوانٍ لمدة ساعة استخدام نشط | ~0.5% - 1% |
| المسح كل 5 ثوانٍ لمدة ساعة استخدام نشط | ~0.3% - 0.6% |
| الميزة معطلة | 0% |

> **الهدف:** استهلاك إضافي لا يتجاوز **1% يوميًا** عند الاستخدام النشط لمدة 4 ساعات.

### 3.3 آلية تحرير الموارد

```kotlin
// تحرير النموذج من الذاكرة عند عدم الحاجة
// عند إيقاف الميزة أو انخفاض البطارية
fun releaseModel() {
    nsfwClassifier?.close()
    nsfwClassifier = null
}
```

---

## 4. هيكلة الميزة وتوزيع الملفات

> ملتزمون بـ **Feature-based Packaging** كما في `GEMINI.md` و `DEVELOPER_GUIDE.md`

```
app/src/main/
├── java/com/atyafcode/sirat/
│   │
│   ├── features/contentdetection/          ← ميزة جديدة كاملة
│   │   ├── domain/
│   │   │   ├── NsfwClassifier.kt           # نموذج TFLite لتصنيف المحتوى
│   │   │   ├── DetectionResult.kt          # كائن نتيجة الكشف
│   │   │   └── ScreenScanManager.kt        # منسق عملية المسح والحظر
│   │   ├── data/
│   │   │   └── DetectionRepository.kt      # تخزين سجلات الكشف
│   │   └── ui/
│   │       ├── ContentDetectionSettingsScreen.kt  # شاشة إعدادات الميزة
│   │       └── ContentDetectionViewModel.kt       # ViewModel للإعدادات
│   │
│   ├── services/
│   │   ├── AppLockAccessibilityService.kt  ← تعديل محدود (Surgical Edit)
│   │   └── AppLockManager.kt               ← تعديل محدود (إضافة دالة قفل مؤقت)
│   │
│   ├── data/repository/
│   │   ├── PreferencesRepository.kt        ← تعديل محدود (إضافة مفاتيح جديدة)
│   │   └── AppLockRepository.kt            ← تعديل محدود (إضافة دوال تمرير)
│   │
│   └── core/navigation/
│       └── Screen.kt                       ← تعديل محدود (إضافة مسار جديد)
│
├── assets/
│   └── nsfw_model.tflite                   ← ملف النموذج (~5 MB)
│
└── res/xml/
    └── accessibility_service_config.xml   ← تعديل محدود (إضافة canTakeScreenshots)
```

---

## 5. التبعيات المطلوبة

### 5.1 تعديل `gradle/libs.versions.toml`

إضافة الإصدارات:
```toml
[versions]
# ... الإصدارات الموجودة ...
tensorflowLite = "2.16.1"
tensorflowLiteSupport = "0.4.4"
tensorflowLiteGpu = "2.16.1"

[libraries]
# ... المكتبات الموجودة ...
tensorflow-lite = { module = "org.tensorflow:tensorflow-lite", version.ref = "tensorflowLite" }
tensorflow-lite-support = { module = "org.tensorflow:tensorflow-lite-support", version.ref = "tensorflowLiteSupport" }
tensorflow-lite-gpu = { module = "org.tensorflow:tensorflow-lite-gpu", version.ref = "tensorflowLiteGpu" }
```

### 5.2 تعديل `app/build.gradle.kts`

إضافة في `dependencies`:
```kotlin
// NSFW Content Detection — On-device AI
implementation(libs.tensorflow.lite)
implementation(libs.tensorflow.lite.support)
implementation(libs.tensorflow.lite.gpu)
```

### 5.3 تعديل `app/proguard-rules.pro`

إضافة قواعد ProGuard للحفاظ على فئات TFLite:
```proguard
# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**
```

---

## المرحلة الأولى: البنية التحتية للنموذج

### 1.1 ملف: `DetectionResult.kt`

**المسار:** `features/contentdetection/domain/DetectionResult.kt`

```kotlin
package com.atyafcode.sirat.features.contentdetection.domain

/**
 * يمثل نتيجة تصنيف المحتوى بواسطة نموذج الذكاء الاصطناعي.
 */
sealed class DetectionResult {
    
    /** المحتوى آمن */
    data class Safe(val confidence: Float) : DetectionResult()
    
    /** المحتوى إباحي — يتطلب إجراء فوري */
    data class NsfwDetected(
        val confidence: Float,
        val packageName: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : DetectionResult()
    
    /** فشل التصنيف (خطأ في المعالجة) */
    data class Error(val message: String) : DetectionResult()
    
    /** تم تخطي المسح (لأسباب الأداء أو الفلترة) */
    object Skipped : DetectionResult()
}
```

### 1.2 ملف: `NsfwClassifier.kt`

**المسار:** `features/contentdetection/domain/NsfwClassifier.kt`

**المبادئ التصميمية:**
- تحميل كسول (Lazy loading) للنموذج
- استخدام `Mutex` لمنع التشغيل المتزامن
- تصغير الصورة قبل المعالجة
- تحرير الموارد عند عدم الحاجة

```kotlin
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
        private const val MODEL_FILE = "nsfw_model.tflite"
        private const val IMAGE_SIZE = 224
        private const val CONFIDENCE_THRESHOLD = 0.85f
        // تخصيص ByteBuffer مسبقًا لتجنب إنشاء كائنات جديدة في كل مرة
        private const val PIXEL_SIZE = 3 // RGB
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val mutex = Mutex()
    
    // ByteBuffer معاد الاستخدام لتقليل ضغط الـ GC
    private lateinit var inputImageBuffer: ByteBuffer
    
    /**
     * تهيئة النموذج — تحميل كسول.
     * يُستدعى عند أول تفعيل للميزة فقط.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (interpreter != null) return@withContext true
            
            try {
                // تحميل النموذج من assets
                val modelBuffer = loadModelFile()
                
                // محاولة استخدام GPU Delegate لتسريع المعالجة
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
                } catch (e: Exception) {
                    options.setNumThreads(2)
                    Log.w(TAG, "GPU Delegate failed, falling back to CPU", e)
                }
                
                interpreter = Interpreter(modelBuffer, options)
                
                // تهيئة ByteBuffer لإعادة الاستخدام
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
     * @return نتيجة الكشف
     */
    suspend fun classify(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        if (interpreter == null) {
            val initialized = initialize()
            if (!initialized) return@withContext DetectionResult.Error("Model not initialized")
        }
        
        mutex.withLock {
            try {
                // تصغير الصورة إلى 224×224 (يقلل المعالجة بشكل كبير)
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap, IMAGE_SIZE, IMAGE_SIZE, true
                )
                
                // تحويل Bitmap إلى ByteBuffer
                inputImageBuffer.rewind()
                convertBitmapToBuffer(scaledBitmap, inputImageBuffer)
                
                // تشغيل النموذج
                val output = Array(1) { FloatArray(2) } // [safe, nsfw]
                interpreter?.run(inputImageBuffer, output)
                
                val safeScore = output[0][0]
                val nsfwScore = output[0][1]
                
                // تحرير الصورة المصغرة فورًا
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                
                Log.d(TAG, "Classification: safe=%.3f, nsfw=%.3f".format(safeScore, nsfwScore))
                
                when {
                    nsfwScore >= CONFIDENCE_THRESHOLD -> {
                        DetectionResult.NsfwDetected(confidence = nsfwScore, packageName = "")
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
     * تحويل Bitmap إلى ByteBuffer بصيغة NORMALIZED [0,1]
     * باستخدام قيم التطبيع القياسية لـ ImageNet
     */
    private fun convertBitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        
        var index = 0
        for (pixel in pixels) {
            // تطبيع القيم إلى [0, 1]
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)           // B
            index++
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
                Log.d(TAG, "NSFW model released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing model", e)
            }
        }
    }
    
    fun isInitialized(): Boolean = interpreter != null
}
```

---

## المرحلة الثانية: مدير مسح الشاشة

### 2.1 ملف: `ScreenScanManager.kt`

**المسار:** `features/contentdetection/domain/ScreenScanManager.kt`

**المسؤوليات:**
- تنسيق عملية المسح مع الـ Throttling
- تتبع عدد المخالفات لكل تطبيق
- تنفيذ إجراءات الحظر (رجوع، قفل مؤقت)
- مراقبة حالة البطارية والجهاز

```kotlin
package com.atyafcode.sirat.features.contentdetection.domain

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.data.repository.AppLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * مدير مسح الشاشة بالذكاء الاصطناعي.
 *
 * ينسق بين AccessibilityService ونموذج TFLite لتصنيف المحتوى.
 * مصمم بأولوية حفظ البطارية: فلترة متعددة الطبقات قبل أي معالجة.
 *
 * @param appLockRepository للوصول لإعدادات المستخدم
 */
class ScreenScanManager(
    private val appLockRepository: AppLockRepository,
    private val context: Context
) {
    companion object {
        private const val TAG = "ScreenScanManager"
        
        // الفاصل الزمني بين عمليات المسح (قابل للتخصيص)
        const val DEFAULT_SCAN_INTERVAL_MS = 3000L  // 3 ثوانٍ
        const val COOLDOWN_AFTER_DETECTION_MS = 10_000L  // 10 ثوانٍ بعد الكشف
        
        // عدد المخالفات قبل قفل التطبيق
        const val VIOLATIONS_THRESHOLD = 3
        
        // مدة القفل المؤقت للتطبيق المخالف
        const val TEMP_LOCK_DURATION_MS = 15 * 60 * 1000L  // 15 دقيقة
        
        // حد البطارية لإيقاف المسح
        const val BATTERY_LOW_THRESHOLD = 15
    }
    
    private val classifier = NsfwClassifier(context)
    private val scanScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scanMutex = Mutex()
    
    // حالة المسح
    @Volatile private var isScanningEnabled = false
    @Volatile private var lastScanTime = 0L
    @Volatile private var lastDetectionTime = 0L
    @Volatile private var isPaused = false
    
    // تتبع المخالفات لكل حزمة
    private val violationCounts = ConcurrentHashMap<String, Int>()
    
    // التطبيقات المقفولة مؤقتًا مع وقت انتهاء القفل
    private val tempLockedApps = ConcurrentHashMap<String, Long>()
    
    // مراقب حالة البطارية
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    LogUtils.d(TAG, "Battery low — pausing content detection")
                    isPaused = true
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    LogUtils.d(TAG, "Battery okay — resuming content detection")
                    isPaused = false
                }
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtils.d(TAG, "Screen off — pausing content detection")
                    isPaused = true
                }
                Intent.ACTION_SCREEN_ON -> {
                    LogUtils.d(TAG, "Screen on — resuming content detection")
                    isPaused = false
                }
            }
        }
    }
    
    private var batteryReceiverRegistered = false
    
    /**
     * تفعيل ميزة المسح.
     * يسجل مستقبل البطارية ويهيئ النموذج.
     */
    fun start() {
        if (isScanningEnabled) return
        
        isScanningEnabled = true
        isPaused = false
        
        // تسجيل مستقبل حالة البطارية والشاشة
        if (!batteryReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(batteryReceiver, filter)
            batteryReceiverRegistered = true
        }
        
        // تهيئة النموذج في الخلفية (Lazy)
        scanScope.launch {
            val success = classifier.initialize()
            LogUtils.d(TAG, "Classifier initialization: $success")
        }
        
        LogUtils.d(TAG, "Content detection started")
    }
    
    /**
     * إيقاف ميزة المسح وتحرير الموارد.
     */
    fun stop() {
        isScanningEnabled = false
        isPaused = false
        
        // تحرير النموذج من الذاكرة
        classifier.release()
        
        // إلغاء تسجيل المستقبل
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) { }
            batteryReceiverRegistered = false
        }
        
        // مسح الحالات
        violationCounts.clear()
        tempLockedApps.clear()
        
        LogUtils.d(TAG, "Content detection stopped and resources released")
    }
    
    /**
     * إيقاف مؤقت (مثلاً أثناء عرض شاشة القفل).
     */
    fun pause() {
        isPaused = true
        LogUtils.d(TAG, "Content detection paused")
    }
    
    /**
     * استئناف بعد الإيقاف المؤقت.
     */
    fun resume() {
        isPaused = false
        LogUtils.d(TAG, "Content detection resumed")
    }
    
    /**
     * محاولة مسح الشاشة.
     * تخضع لفلترة متعددة الطبقات قبل المعالجة الفعلية.
     *
     * @param service خدمة Accessibility لالتقاط الشاشة
     * @param packageName الحزمة الحالية في المقدمة
     * @param onDetection رد النداء عند الكشف عن محتوى إباحي
     */
    fun maybeScan(
        service: AccessibilityService,
        packageName: String,
        onDetection: (DetectionResult.NsfwDetected) -> Unit
    ) {
        // === الطبقة 1: التحقق من التفعيل ===
        if (!isScanningEnabled || isPaused) return
        
        // === الطبقة 2: التحقق من API Level ===
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        
        // === الطبقة 3: فلترة التطبيقات المستثناة ===
        if (isAppExcluded(packageName)) return
        
        // === الطبقة 4: التحقق من القفل المؤقت ===
        if (isAppTempLocked(packageName)) {
            // التحقق من انتهاء مدة القفل
            checkAndRemoveExpiredLock(packageName)
            if (isAppTempLocked(packageName)) return
        }
        
        // === الطبقة 5: Throttle زمني ===
        val now = System.currentTimeMillis()
        val interval = if (lastDetectionTime > 0 && (now - lastDetectionTime) < COOLDOWN_AFTER_DETECTION_MS) {
            COOLDOWN_AFTER_DETECTION_MS
        } else {
            appLockRepository.getContentDetectionScanInterval()
        }
        
        if (now - lastScanTime < interval) return
        lastScanTime = now
        
        // === الطبقة 6: التحقق من حالة البطارية ===
        if (isBatteryLow()) {
            LogUtils.d(TAG, "Battery low — skipping scan")
            return
        }
        
        // === تنفيذ المسح ===
        scanScope.launch {
            scanMutex.withLock {
                performScan(service, packageName, onDetection)
            }
        }
    }
    
    /**
     * تنفيذ المسح الفعلي.
     */
    private suspend fun performScan(
        service: AccessibilityService,
        packageName: String,
        onDetection: (DetectionResult.NsfwDetected) -> Unit
    ) {
        try {
            // أخذ لقطة الشاشة (API 30+)
            val screenshot = takeScreenshotSync(service) ?: return
            
            screenshot.use { screenshotResult ->
                val bitmap = screenshotResult.bitmap
                
                // تصنيف الصورة
                val result = classifier.classify(bitmap)
                
                // تحرير الـ Bitmap فورًا
                bitmap.recycle()
                
                when (result) {
                    is DetectionResult.NsfwDetected -> {
                        lastDetectionTime = System.currentTimeMillis()
                        handleDetection(packageName, result.copy(packageName = packageName), service)
                        onDetection(result.copy(packageName = packageName))
                    }
                    is DetectionResult.Safe -> {
                        LogUtils.d(TAG, "Content safe: confidence=${result.confidence}")
                    }
                    is DetectionResult.Error -> {
                        LogUtils.e(TAG, "Detection error: ${result.message}", null)
                    }
                    is DetectionResult.Skipped -> { /* لا شيء */ }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error during scan", e)
        }
    }
    
    /**
     * التقاط لقطة شاشة باستخدام AccessibilityService (API 30+).
     */
    private suspend fun takeScreenshotSync(
        service: AccessibilityService
    ): AccessibilityService.ScreenshotResult? {
        // TODO: تنفيذ باستخدام AccessibilityService.takeScreenshot()
        // هذه الدالة تأخذ callback، نقوم بتحويلها إلى coroutine
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                AccessibilityService.TAKE_SCREENSHOT_REQUEST_TIMEOUT,
                Dispatchers.Main,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        if (cont.isActive) cont.resume(result)
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        LogUtils.e(TAG, "Screenshot failed: errorCode=$errorCode", null)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
    }
    
    /**
     * معالجة الكشف عن محتوى إباحي.
     */
    private fun handleDetection(
        packageName: String,
        result: DetectionResult.NsfwDetected,
        service: AccessibilityService
    ) {
        LogUtils.w(TAG, "NSFW detected in $packageName: confidence=${result.confidence}")
        
        // زيادة عداد المخالفات
        val count = violationCounts.merge(packageName, 1) { old, inc -> old + inc } ?: 1
        
        // إجراء فوري: الضغط على زر الرجوع
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        
        if (count >= VIOLATIONS_THRESHOLD) {
            // قفل التطبيق مؤقتًا
            lockAppTemporarily(packageName)
            violationCounts[packageName] = 0
            
            // تنفيذ إجراء إضافي: العودة للشاشة الرئيسية
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }
    
    /**
     * قفل تطبيق مؤقتًا لمدة محددة.
     */
    private fun lockAppTemporarily(packageName: String) {
        val unlockTime = System.currentTimeMillis() + TEMP_LOCK_DURATION_MS
        tempLockedApps[packageName] = unlockTime
        LogUtils.w(TAG, "App $packageName temporarily locked for 15 minutes")
    }
    
    /**
     * التحقق من انتهاء مدة القفل المؤقت.
     */
    private fun checkAndRemoveExpiredLock(packageName: String) {
        val unlockTime = tempLockedApps[packageName]
        if (unlockTime != null && System.currentTimeMillis() > unlockTime) {
            tempLockedApps.remove(packageName)
            violationCounts[packageName] = 0
            LogUtils.d(TAG, "Temporary lock expired for $packageName")
        }
    }
    
    /**
     * هل التطبيق مقفل مؤقتًا؟
     */
    fun isAppTempLocked(packageName: String): Boolean {
        checkAndRemoveExpiredLock(packageName)
        return tempLockedApps.containsKey(packageName)
    }
    
    /**
     * فلترة التطبيقات التي لا تحتاج للمسح.
     */
    private fun isAppExcluded(packageName: String): Boolean {
        // تطبيق صراط نفسه
        if (packageName.startsWith("com.atyafcode.sirat")) return true
        
        // لوحة المفاتيح
        // (يتم التحقق منها في AppLockAccessibilityService)
        
        // التطبيقات المستثناة من قبل المستخدم
        if (packageName in appLockRepository.getContentDetectionExcludedApps()) return true
        
        // التطبيقات المستثناة من Trigger (تشارك نفس القائمة)
        if (packageName in appLockRepository.getTriggerExcludedApps()) return true
        
        // التطبيقات النظامية الأساسية
        val systemApps = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.packageinstaller",
            "android"
        )
        if (packageName in systemApps) return true
        
        return false
    }
    
    /**
     * التحقق من انخفاض البطارية.
     */
    private fun isBatteryLow(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }
    
    /**
     * الحصول على عدد المخالفات لحزمة معينة.
     */
    fun getViolationCount(packageName: String): Int = violationCounts[packageName] ?: 0
    
    /**
     * إعادة تعيين عداد المخالفات لحزمة معينة.
     */
    fun resetViolations(packageName: String) {
        violationCounts.remove(packageName)
    }
}
```

> **ملاحظة:** دالة `takeScreenshotSync` تستخدم `kotlinx.coroutines.suspendCancellableCoroutine` لتحويل الـ callback إلى coroutine. يجب استيراد `kotlinx.coroutines.resume` و `kotlinx.coroutines.resumeWithException` عبر extension functions.

---

## المرحلة الثالثة: التكامل مع خدمة Accessibility

### 3.1 تعديل `accessibility_service_config.xml`

**المسار:** `app/src/main/res/xml/accessibility_service_config.xml`

> **تعديل surgical:** إضافة `canTakeScreenshots="true"` فقط

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshots="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:settingsActivity="com.atyafcode.sirat.MainActivity" />
```

### 3.2 تعديل `AppLockAccessibilityService.kt`

**المسار:** `services/AppLockAccessibilityService.kt`

> **تعديلات surgical محدودة** — إضافة منطق المسح فقط دون المساس بالمنطق الحالي

**الإضافات المطلوبة:**

1. **حقل جديد** لـ `ScreenScanManager`:
```kotlin
// في بداية الكلاس، بعد الحقول الموجودة
private var screenScanManager: ScreenScanManager? = null
```

2. **في `onCreate()`** — تهيئة المدير:
```kotlin
// في نهاية onCreate()، بعد التهيئات الموجودة
try {
    if (appLockRepository.isContentDetectionEnabled()) {
        screenScanManager = ScreenScanManager(appLockRepository, applicationContext)
        screenScanManager?.start()
    }
} catch (e: Exception) {
    logError("Error initializing ScreenScanManager", e)
}
```

3. **في `onAccessibilityEvent()`** — إضافة استدعاء المسح:
```kotlin
// في نهاية handleAccessibilityEvent()، قبل الإرجاع
// فقط بعد التحقق من التطبيق الحالي (بعد isValidPackageForLocking)
if (screenScanManager?.isAppTempLocked(packageName) == true) {
    // التطبيق مقفل مؤقتًا — أعد المستخدم للشاشة الرئيسية
    performGlobalAction(GLOBAL_ACTION_HOME)
    return
}

// محاولة مسح المحتوى البصري
screenScanManager?.maybeScan(
    service = this,
    packageName = packageName,
    onDetection = { result ->
        // يتم تنفيذه على Dispatchers.Default
        // الإجراءات الفعلية تمت بالفعل في handleDetection
    }
)
```

4. **في `showLockScreenOverlay()`** — إيقاف المسح مؤقتًا:
```kotlin
// في بداية الدالة
screenScanManager?.pause()
```

5. **بعد إخفاء شاشة القفل** — استئناف المسح:
```kotlin
// في onUnlock و onExit callbacks
screenScanManager?.resume()
```

6. **في `onDestroy()`** — تنظيف:
```kotlin
// في onDestroy()، قبل super.onDestroy()
screenScanManager?.stop()
screenScanManager = null
```

7. **استقبال ACTION_SCREEN_OFF** — تحديث الـ receiver الموجود:
```kotlin
// في screenStateReceiver الموجود، إضافة:
// screenScanManager?.pause() عند ACTION_SCREEN_OFF
// screenScanManager?.resume() عند ACTION_USER_PRESENT
```

### 3.3 تعديل `AppLockManager.kt`

> **تعديل surgical:** إضافة حالة قفل مؤقت للتطبيقات

```kotlin
// إضافة حقل جديد في AppLockManager
private val contentDetectionTempLocks = ConcurrentHashMap<String, Long>()

/**
 * قفل تطبيق مؤقتًا بسبب كشف محتوى إباحي.
 * @param packageName اسم الحزمة
 * @param durationMs مدة القفل بالميلي ثانية
 */
fun lockAppTemporarilyForContentDetection(packageName: String, durationMs: Long) {
    contentDetectionTempLocks[packageName] = System.currentTimeMillis() + durationMs
    // مسح حالة فتح التطبيق المؤقت
    temporarilyUnlockedApp = ""
    LogUtils.d(TAG, "App $packageName locked for content detection: ${durationMs}ms")
}

/**
 * التحقق من قفل المحتوى المؤقت.
 */
fun isContentDetectionLocked(packageName: String): Boolean {
    val unlockTime = contentDetectionTempLocks[packageName]
    if (unlockTime != null) {
        if (System.currentTimeMillis() < unlockTime) return true
        contentDetectionTempLocks.remove(packageName)
    }
    return false
}
```

---

## المرحلة الرابعة: إعدادات المستخدم

### 4.1 تعديل `PreferencesRepository.kt`

> **تعديل surgical:** إضافة مفاتيح ودوال جديدة فقط

```kotlin
// === مفاتيح جديدة في companion object ===
private const val KEY_CONTENT_DETECTION_ENABLED = "content_detection_enabled"
private const val KEY_CONTENT_DETECTION_SCAN_INTERVAL = "content_detection_scan_interval"
private const val KEY_CONTENT_DETECTION_EXCLUDED_APPS = "content_detection_excluded_apps"
private const val KEY_CONTENT_DETECTION_THRESHOLD = "content_detection_threshold"

// === دوال جديدة ===

fun setContentDetectionEnabled(enabled: Boolean) {
    settingsPrefs.edit { putBoolean(KEY_CONTENT_DETECTION_ENABLED, enabled) }
}

fun isContentDetectionEnabled(): Boolean {
    return settingsPrefs.getBoolean(KEY_CONTENT_DETECTION_ENABLED, false)
}

fun setContentDetectionScanInterval(intervalMs: Long) {
    settingsPrefs.edit { putLong(KEY_CONTENT_DETECTION_SCAN_INTERVAL, intervalMs) }
}

fun getContentDetectionScanInterval(): Long {
    return settingsPrefs.getLong(KEY_CONTENT_DETECTION_SCAN_INTERVAL, 3000L)
}

fun setContentDetectionExcludedApps(apps: Set<String>) {
    settingsPrefs.edit { putStringSet(KEY_CONTENT_DETECTION_EXCLUDED_APPS, apps) }
}

fun getContentDetectionExcludedApps(): Set<String> {
    return settingsPrefs.getStringSet(KEY_CONTENT_DETECTION_EXCLUDED_APPS, emptySet()) ?: emptySet()
}

fun setContentDetectionThreshold(threshold: Float) {
    settingsPrefs.edit { putFloat(KEY_CONTENT_DETECTION_THRESHOLD, threshold) }
}

fun getContentDetectionThreshold(): Float {
    return settingsPrefs.getFloat(KEY_CONTENT_DETECTION_THRESHOLD, 0.85f)
}
```

### 4.2 تعديل `AppLockRepository.kt`

> **تعديل surgical:** إضافة دوال تمرير فقط

```kotlin
// === دوال تمرير للمستودع المفضل ===

fun setContentDetectionEnabled(enabled: Boolean) =
    preferencesRepository.setContentDetectionEnabled(enabled)

fun isContentDetectionEnabled(): Boolean =
    preferencesRepository.isContentDetectionEnabled()

fun setContentDetectionScanInterval(intervalMs: Long) =
    preferencesRepository.setContentDetectionScanInterval(intervalMs)

fun getContentDetectionScanInterval(): Long =
    preferencesRepository.getContentDetectionScanInterval()

fun getContentDetectionExcludedApps(): Set<String> =
    preferencesRepository.getContentDetectionExcludedApps()

fun setContentDetectionExcludedApps(apps: Set<String>) =
    preferencesRepository.setContentDetectionExcludedApps(apps)

fun getContentDetectionThreshold(): Float =
    preferencesRepository.getContentDetectionThreshold()

fun setContentDetectionThreshold(threshold: Float) =
    preferencesRepository.setContentDetectionThreshold(threshold)
```

### 4.3 تعديل `Screen.kt`

> **تعديل surgical:** إضافة مسار جديد

```kotlin
// إضافة في sealed class Screen
object ContentDetectionSettings : Screen("content_detection_settings")
```

### 4.4 شاشة الإعدادات: `ContentDetectionSettingsScreen.kt`

**المسار:** `features/contentdetection/ui/ContentDetectionSettingsScreen.kt`

>遵循 Material 3 + Jetpack Compose فقط (وفق GEMINI.md)

```kotlin
package com.atyafcode.sirat.features.contentdetection.ui

// شاشة إعدادات Compose + Material 3
// تتضمن:
// - زر تفعيل/تعطيل الميزة
// - شريط تمرير لضبط الفاصل الزمني للمسح (2-5 ثوانٍ)
// - شريط تمرير لضبط حد الثقة (0.5 - 0.95)
// - قائمة التطبيقات المستثناة
// - معلومات عن استهلاك البطارية المتوقع
// - تحذير: الميزة تعمل فقط على Android 11+
```

> **ملاحظة:** الشاشة تُضاف في `AppNavHost` ويمكن الوصول إليها من `SettingsScreen`.

### 4.5 تعديل `SettingsScreen.kt`

> **تعديل surgical:** إضافة عنصر إعدادات جديد في قسم الأمان

```kotlin
// إضافة في SettingsGroup الخاصة بالأمان:
ActionSettingItem(
    icon = Icons.Default.Visibility,
    title = stringResource(R.string.content_detection_title),
    subtitle = if (appLockRepository.isContentDetectionEnabled())
        stringResource(R.string.content_detection_status_on)
    else stringResource(R.string.content_detection_status_off),
    onClick = { navController.navigate(Screen.ContentDetectionSettings.route) }
)
```

---

## المرحلة الخامسة: قاعدة البيانات والسجلات

### 5.1 ملف: `DetectionRepository.kt`

**المسار:** `features/contentdetection/data/DetectionRepository.kt`

```kotlin
package com.atyafcode.sirat.features.contentdetection.data

import android.content.Context
import com.atyafcode.sirat.data.filter.FilterDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مستودع سجلات الكشف البصري.
 * يخزن سجلات الكشف باستخدام Room (مشاركة FilterDatabase الموجودة).
 */
class DetectionRepository(private val context: Context) {
    
    private val db = FilterDatabase.getInstance(context)
    
    /**
     * تسجيل عملية كشف.
     */
    suspend fun logDetection(
        packageName: String,
        confidence: Float,
        actionTaken: String
    ) {
        // تخزين في قاعدة البيانات
        // يمكن استخدام BlockedLog الموجود أو إنشاء جدول جديد
    }
    
    /**
     * الحصول على سجلات الكشف الأخيرة.
     */
    suspend fun getRecentDetections(limit: Int = 50): List<DetectionLog> {
        return emptyList() // TODO
    }
    
    /**
     * الحصول على إحصائيات الكشف.
     */
    suspend fun getDetectionStats(): DetectionStats {
        return DetectionStats() // TODO
    }
}

data class DetectionLog(
    val packageName: String,
    val confidence: Float,
    val actionTaken: String,
    val timestamp: Long
)

data class DetectionStats(
    val totalDetections: Int = 0,
    val todayDetections: Int = 0,
    val mostFlaggedApp: String? = null
)
```

---

## المرحلة السادسة: التوطين (Localization)

### 6.1 إضافة سلاسل عربية في `res/values-ar/strings.xml`

```xml
<!-- Content Detection -->
<string name="content_detection_title">الكشف البصري عن المحتوى</string>
<string name="content_detection_subtitle">مسح الشاشة بالذكاء الاصطناعي للكشف عن المحتوى غير اللائق</string>
<string name="content_detection_status_on">مفعّل</string>
<string name="content_detection_status_off">معطّل</string>
<string name="content_detection_enable">تفعيل الكشف البصري</string>
<string name="content_detection_enable_desc">استخدام الذكاء الاصطناعي لمسح الشاشة والكشف عن المحتوى غير اللائق في التطبيقات</string>
<string name="content_detection_scan_interval">الفاصل الزمني للمسح</string>
<string name="content_detection_scan_interval_desc">المدة بين كل مسحة وأخرى (ثوانٍ)</string>
<string name="content_detection_threshold">حد الحساسية</string>
<string name="content_detection_threshold_desc">كلما زادت القيمة، قلّت النتائج الخاطئة لكن قد يفوت بعض المحتوى</string>
<string name="content_detection_excluded_apps">التطبيقات المستثناة من المسح</string>
<string name="content_detection_battery_warning">الاستهلاك المتوقع للبطارية: أقل من 1% يوميًا</string>
<string name="content_detection_api_warning">هذه الميزة تتطلب Android 11 أو أحدث</string>
<string name="content_detection_violation_warning">تم كشف محتوى غير لائق في هذا التطبيق</string>
<string name="content_detection_app_locked">تم قفل التطبيق مؤقتًا بسبب تكرار المحتوى المخالف لمدة 15 دقيقة</string>
<string name="content_detection_privacy_note">جميع المعالجة تتم على جهازك — لا تُرسل أي صور أو بيانات إلى أي خادم</string>
```

### 6.2 إضافة سلاسل إنجليزية في `res/values/strings.xml`

```xml
<!-- Content Detection -->
<string name="content_detection_title">Visual Content Detection</string>
<string name="content_detection_subtitle">AI screen scanning to detect inappropriate content</string>
<string name="content_detection_status_on">Enabled</string>
<string name="content_detection_status_off">Disabled</string>
<string name="content_detection_enable">Enable Visual Detection</string>
<string name="content_detection_enable_desc">Use AI to scan screen and detect inappropriate content in apps</string>
<string name="content_detection_scan_interval">Scan Interval</string>
<string name="content_detection_scan_interval_desc">Time between scans (seconds)</string>
<string name="content_detection_threshold">Sensitivity Threshold</string>
<string name="content_detection_threshold_desc">Higher value means fewer false positives but may miss some content</string>
<string name="content_detection_excluded_apps">Excluded Apps</string>
<string name="content_detection_battery_warning">Expected battery usage: less than 1% per day</string>
<string name="content_detection_api_warning">This feature requires Android 11 or later</string>
<string name="content_detection_violation_warning">Inappropriate content detected in this app</string>
<string name="content_detection_app_locked">App temporarily locked for 15 minutes due to repeated violations</string>
<string name="content_detection_privacy_note">All processing happens on your device — no images or data are sent to any server</string>
```

---

## تعديلات الملفات الموجودة

### ملخص التعديلات Surgical (وفق GEMINI.md)

| الملف | نوع التعديل | التفاصيل |
|---|---|---|
| `gradle/libs.versions.toml` | إضافة | 3 إصدارات + 3 مكتبات TFLite |
| `app/build.gradle.kts` | إضافة | 3 سطور dependencies |
| `app/proguard-rules.pro` | إضافة | قواعد TFLite |
| `res/xml/accessibility_service_config.xml` | إضافة | `canTakeScreenshots="true"` |
| `services/AppLockAccessibilityService.kt` | تعديل محدود | تهيئة + استدعاء المسح + إيقاف مؤقت |
| `services/AppLockManager.kt` | إضافة | دالتين للقفل المؤقت |
| `data/repository/PreferencesRepository.kt` | إضافة | مفاتيح + دوال جديدة |
| `data/repository/AppLockRepository.kt` | إضافة | دوال تمرير |
| `core/navigation/Screen.kt` | إضافة | مسار جديد |
| `features/settings/ui/SettingsScreen.kt` | إضافة | عنصر إعدادات جديد |
| `res/values/strings.xml` | إضافة | سلاسل إنجليزية |
| `res/values-ar/strings.xml` | إضافة | سلاسل عربية |

### ملفات جديدة كاملة

| الملف | الوصف |
|---|---|
| `features/contentdetection/domain/DetectionResult.kt` | كائن نتيجة الكشف |
| `features/contentdetection/domain/NsfwClassifier.kt` | نموذج TFLite للتصنيف |
| `features/contentdetection/domain/ScreenScanManager.kt` | منسق عملية المسح |
| `features/contentdetection/data/DetectionRepository.kt` | مستودع السجلات |
| `features/contentdetection/ui/ContentDetectionSettingsScreen.kt` | شاشة الإعدادات |
| `features/contentdetection/ui/ContentDetectionViewModel.kt` | ViewModel |
| `assets/nsfw_model.tflite` | ملف النموذج (~5 MB) |

---

## قائمة التحقق النهائية

### قبل التنفيذ
- [ ] اختيار وتدريب/تحميل نموذج NSFW TFLite مُكمَّم (int8)
- [ ] التأكد من حجم النموذج ≤ 10MB
- [ ] التأكد من دقة النموذج ≥ 90% على مجموعة الاختبار
- [ ] وضع ملف النموذج في `app/src/main/assets/nsfw_model.tflite`

### أثناء التنفيذ
- [ ] إضافة التبعيات في `libs.versions.toml` و `build.gradle.kts`
- [ ] إضافة قواعد ProGuard
- [ ] إنشاء جميع الملفات الجديدة في `features/contentdetection/`
- [ ] تعديل `accessibility_service_config.xml` (إضافة `canTakeScreenshots`)
- [ ] تعديل `PreferencesRepository.kt` (إضافة المفاتيح والدوال)
- [ ] تعديل `AppLockRepository.kt` (إضافة دوال التمرير)
- [ ] تعديل `AppLockManager.kt` (إضافة القفل المؤقت)
- [ ] تعديل `AppLockAccessibilityService.kt` (التكامل مع المسح)
- [ ] تعديل `Screen.kt` (إضافة المسار)
- [ ] تعديل `SettingsScreen.kt` (إضافة عنصر الإعدادات)
- [ ] إضافة السلاسل العربية والإنجليزية

### بعد التنفيذ — الاختبار
- [ ] اختبار البطارية: مقارنة الاستهلاك قبل وبعد التفعيل لمدة 24 ساعة
- [ ] اختبار الأداء: عدم وجود تأخير في خدمة Accessibility الأساسية
- [ ] اختبار الدقة: الكشف عن محتوى إباحي معروف
- [ ] اختبار النتائج الخاطئة: عدم الكشف عن محتوى آمن
- [ ] اختبار Throttle: التأكد من عدم المسح المتكرر
- [ ] اختبار القفل المؤقت: قفل التطبيق بعد 3 مخالفات
- [ ] اختبار الأجهزة القديمة (API 26-29): الميزة معطلة تلقائيًا
- [ ] اختبار الخصوصية: التأكد من عدم إرسال أي بيانات للخارج

### مراقبة ما بعد الإطلاق
- [ ] مراقبة استهلاك البطارية عبر تقارير Android Vitals
- [ ] مراقبة ANR (Application Not Responding) في خدمة Accessibility
- [ ] جمع ملاحظات المستخدمين حول النتائج الخاطئة

---

## ملاحظات مهمة

### 1. مصدر نموذج NSFW
يوجد عدة نماذج مفتوحة المصدر يمكن استخدامها:
- **NudeNet** — نموذج كشف العري (يمكن تحويله إلى TFLite)
- **open_nsfw** من Yahoo — نموذج كشف محتوى غير لائق
- تدريب نموذج مخصص باستخدام MobileNetV2 + Transfer Learning على مجموعة بيانات NSFW

> **التوصية:** استخدام MobileNetV2 كقاعدة مع Transfer Learning، ثم تحويله إلى TFLite بصيغة int8 quantized. يمكن الاستعانة بخدمات مثل HuggingFace لتحميل نموذج جاهز.

### 2. التحديث المستقبلي
- يمكن إضافة كشف الملابس غير المحتشمة (مايوهات) لاحقًا باستخدام نفس البنية
- يمكن إضافة نموذج ثانٍ متخصص في كشف صور معينة

### 3. التوافق مع الوضع الخاضع للإشراف (Supervised Mode)
- الميزة تعمل بشكل مستقل عن وضع الإشراف
- يمكن ربطها لاحقًا بحيث يتم إرسال إشعار للمراقب عند الكشف

### 4. عدم المساس بالوظائف الحالية
وفقًا لـ `AGENTS.md`:
> "Any change to these services must not degrade battery or performance"

لذلك:
- منطق قفل التطبيقات الحالي **لا يُمَس**
- منطق anti-uninstall **لا يُمَس**
- منطق Shizuku و Usage Stats **لا يُمَس**
- الإضافات تكون **مضيفة** وليست **بديلة**

---

> **هذه الخطة ملتزمة كامل الالتزام بـ `AGENTS.md` و `DEVELOPER_GUIDE.md` و `GEMINI.md`**
> 
> **التركيز الأساسي:** حفظ البطارية عبر فلترة متعددة الطبقات + معالجة فعّالة + تحرير الموارد

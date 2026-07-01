package com.atyafcode.sirat.features.contentdetection.domain

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.atyafcode.sirat.core.utils.LogUtils
import com.atyafcode.sirat.data.repository.AppLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * مدير مسح الشاشة بالذكاء الاصطناعي.
 *
 * ينسق بين AccessibilityService ونموذج TFLite لتصنيف المحتوى.
 * مصمم بأولوية حفظ البطارية: فلترة متعددة الطبقات قبل أي معالجة.
 *
 * @param appLockRepository للوصول لإعدادات المستخدم
 * @param context سياق التطبيق
 */
class ScreenScanManager(
    private val appLockRepository: AppLockRepository,
    private val context: Context
) {
    companion object {
        private const val TAG = "ScreenScanManager"

        const val DEFAULT_SCAN_INTERVAL_MS = 3000L  // 3 ثوانٍ
        const val COOLDOWN_AFTER_DETECTION_MS = 10_000L  // 10 ثوانٍ بعد الكشف
        const val VIOLATIONS_THRESHOLD = 3
        const val TEMP_LOCK_DURATION_MS = 15 * 60 * 1000L  // 15 دقيقة
        const val BATTERY_LOW_THRESHOLD = 15
    }

    private val classifier = NsfwClassifier(context)
    private val scanScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scanMutex = Mutex()

    @Volatile
    private var isScanningEnabled = false
    @Volatile
    private var lastScanTime = 0L
    @Volatile
    private var lastDetectionTime = 0L
    @Volatile
    private var isPaused = false

    // تتبع المخالفات لكل حزمة
    private val violationCounts = ConcurrentHashMap<String, Int>()

    // التطبيقات المقفولة مؤقتًا مع وقت انتهاء القفل
    private val tempLockedApps = ConcurrentHashMap<String, Long>()

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

        classifier.release()

        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }

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
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun performScan(
        service: AccessibilityService,
        packageName: String,
        onDetection: (DetectionResult.NsfwDetected) -> Unit
    ) {
        var hwBuffer: android.hardware.HardwareBuffer? = null
        try {
            val screenshot = takeScreenshotSync(service) ?: return
            hwBuffer = screenshot.hardwareBuffer
            val colorSpace = screenshot.colorSpace
            val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace) ?: return

            // تحديث عتبة الثقة من إعدادات المستخدم
            classifier.confidenceThreshold = appLockRepository.getContentDetectionThreshold()

            // تصنيف الصورة
            val result = classifier.classify(bitmap, packageName)

            // تحرير الـ Bitmap فورًا
            bitmap.recycle()

            when (result) {
                is DetectionResult.NsfwDetected -> {
                    lastDetectionTime = System.currentTimeMillis()
                    handleDetection(packageName, result, service)
                    onDetection(result)
                }

                is DetectionResult.Safe -> {
                    LogUtils.d(TAG, "Content safe: confidence=${result.confidence}")
                }

                is DetectionResult.Error -> {
                    LogUtils.e(TAG, "Detection error: ${result.message}", null)
                }

                is DetectionResult.Skipped -> { /* لا شيء */ }
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error during scan", e)
        } finally {
            hwBuffer?.close()
        }
    }

    /**
     * التقاط لقطة شاشة باستخدام AccessibilityService (API 30+).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotSync(
        service: AccessibilityService
    ): AccessibilityService.ScreenshotResult? {
        return suspendCancellableCoroutine { cont ->
            try {
                // takeScreenshot يأخذ Executor وليس CoroutineDispatcher
                val executor = Executors.newSingleThreadExecutor()
                service.takeScreenshot(
                    5000, // مهلة 5 ثوانٍ (بالمللي ثانية)
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            executor.shutdown()
                            if (cont.isActive) cont.resume(result)
                        }

                        override fun onFailure(errorCode: Int) {
                            executor.shutdown()
                            LogUtils.e(TAG, "Screenshot failed: errorCode=$errorCode", null)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                LogUtils.e(TAG, "Exception taking screenshot", e)
                if (cont.isActive) cont.resume(null)
            }
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
        LogUtils.d(TAG, "NSFW detected in $packageName: confidence=${result.confidence}")

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
        LogUtils.d(TAG, "App $packageName temporarily locked for 15 minutes")
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
     * التحقق من وضع توفير الطاقة.
     */
    private fun isBatteryLow(): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isPowerSaveMode
        } catch (e: Exception) {
            Log.e(TAG, "Error checking power save mode", e)
            false
        }
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

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

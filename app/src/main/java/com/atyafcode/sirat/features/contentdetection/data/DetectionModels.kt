package com.atyafcode.sirat.features.contentdetection.data

/**
 * سجل عملية كشف بصري واحدة.
 */
data class DetectionLog(
    val packageName: String,
    val confidence: Float,
    val actionTaken: String,
    val timestamp: Long
)

/**
 * إحصائيات الكشف البصري.
 */
data class DetectionStats(
    val totalDetections: Int = 0,
    val todayDetections: Int = 0,
    val mostFlaggedApp: String? = null
)

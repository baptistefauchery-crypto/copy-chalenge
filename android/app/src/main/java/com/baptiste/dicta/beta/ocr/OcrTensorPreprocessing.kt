package com.baptiste.dicta.beta.ocr

internal enum class OcrTensorPreprocessing { DETECTION, RECOGNITION }

/** Mirrors the preprocessing declared by each bundled PaddleOCR model. */
internal fun normalizeOcrTensorComponent(
    component: Int,
    channel: Int,
    preprocessing: OcrTensorPreprocessing,
): Float {
    val clamped = component.coerceIn(0, 255) / 255f
    return when (preprocessing) {
        OcrTensorPreprocessing.DETECTION -> {
            val means = floatArrayOf(0.485f, 0.456f, 0.406f)
            val standardDeviations = floatArrayOf(0.229f, 0.224f, 0.225f)
            val safeChannel = channel.coerceIn(means.indices)
            (clamped - means[safeChannel]) / standardDeviations[safeChannel]
        }
        OcrTensorPreprocessing.RECOGNITION -> (clamped - 0.5f) / 0.5f
    }
}

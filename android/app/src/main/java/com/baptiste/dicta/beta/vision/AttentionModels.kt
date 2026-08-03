package com.baptiste.dicta.beta.vision

enum class AttentionState { SCREEN, NOTEBOOK, UNKNOWN }

data class AttentionFeatures(
    val headPitch: Double,
    val headYaw: Double,
    val leftIrisX: Double,
    val leftIrisY: Double,
    val rightIrisX: Double,
    val rightIrisY: Double,
    val leftEyeOpen: Double,
    val rightEyeOpen: Double,
)

data class CalibrationSample(
    val mean: AttentionFeatures,
    val deviation: AttentionFeatures,
    val sampleCount: Int,
)

data class AttentionCalibration(val screen: CalibrationSample, val quality: Double = 1.0)

data class AttentionReading(
    val state: AttentionState,
    val confidence: Double,
    val faceDetected: Boolean,
    val timestamp: Long,
    val features: AttentionFeatures? = null,
)

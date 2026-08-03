package com.baptiste.dicta.beta.vision

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

data class Landmark(val x: Double, val y: Double, val z: Double = 0.0)

fun extractAttentionFeatures(landmarks: List<Landmark>): AttentionFeatures? {
    if (landmarks.size < 478) return null
    fun distance(a: Landmark, b: Landmark) = hypot(hypot(a.x - b.x, a.y - b.y), a.z - b.z)
    fun average(vararg values: Landmark) = Landmark(
        values.map { it.x }.average(), values.map { it.y }.average(), values.map { it.z }.average(),
    )
    fun ratio(value: Double, first: Double, second: Double) = (value - first) / max(abs(second - first), 1e-5)
    val leftOuter = landmarks[33]
    val leftInner = landmarks[133]
    val rightInner = landmarks[362]
    val rightOuter = landmarks[263]
    val eyeMid = average(leftOuter, leftInner, rightInner, rightOuter)
    val faceWidth = max(distance(landmarks[234], landmarks[454]), 1e-5)
    val faceHeight = max(distance(landmarks[10], landmarks[152]), 1e-5)
    val nose = landmarks[1]
    val leftIris = average(landmarks[468], landmarks[469], landmarks[470], landmarks[471], landmarks[472])
    val rightIris = average(landmarks[473], landmarks[474], landmarks[475], landmarks[476], landmarks[477])
    val leftTop = average(landmarks[159], landmarks[160], landmarks[158])
    val leftBottom = average(landmarks[145], landmarks[144], landmarks[153])
    val rightTop = average(landmarks[386], landmarks[385], landmarks[387])
    val rightBottom = average(landmarks[374], landmarks[380], landmarks[373])
    return AttentionFeatures(
        headPitch = (nose.y - eyeMid.y) / faceHeight,
        headYaw = (nose.x - eyeMid.x) / faceWidth,
        leftIrisX = ratio(leftIris.x, leftOuter.x, leftInner.x),
        leftIrisY = ratio(leftIris.y, leftTop.y, leftBottom.y),
        rightIrisX = ratio(rightIris.x, rightInner.x, rightOuter.x),
        rightIrisY = ratio(rightIris.y, rightTop.y, rightBottom.y),
        leftEyeOpen = distance(leftTop, leftBottom) / max(distance(leftOuter, leftInner), 1e-5),
        rightEyeOpen = distance(rightTop, rightBottom) / max(distance(rightOuter, rightInner), 1e-5),
    )
}

private val featureSelectors: List<(AttentionFeatures) -> Double> = listOf(
    { it.headPitch }, { it.headYaw }, { it.leftIrisX }, { it.leftIrisY },
    { it.rightIrisX }, { it.rightIrisY }, { it.leftEyeOpen }, { it.rightEyeOpen },
)

fun summarizeSamples(samples: List<AttentionFeatures>): CalibrationSample {
    require(samples.size >= 5) { "Calibration requires at least five face samples" }
    val means = featureSelectors.map { selector -> samples.map(selector).average() }
    val deviations = featureSelectors.mapIndexed { index, selector ->
        sqrt(samples.map { (selector(it) - means[index]) * (selector(it) - means[index]) }.average())
    }
    fun feature(values: List<Double>) = AttentionFeatures(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7])
    return CalibrationSample(feature(means), feature(deviations), samples.size)
}

fun classifyFeatures(sample: AttentionFeatures, calibration: AttentionCalibration): Pair<AttentionState, Double> {
    val screen = calibration.screen
    fun tolerance(deviation: Double, floor: Double) = max(floor, deviation * 3)
    val headFacesScreen = abs(sample.headPitch - screen.mean.headPitch) <= tolerance(screen.deviation.headPitch, 0.045) &&
        abs(sample.headYaw - screen.mean.headYaw) <= tolerance(screen.deviation.headYaw, 0.06)
    fun eyeDistance(x: Double, y: Double, screenX: Double, screenY: Double, dx: Double, dy: Double): Double {
        val normalizedX = (x - screenX) / tolerance(dx, 0.09)
        val normalizedY = (y - screenY) / tolerance(dy, 0.10)
        return hypot(normalizedX, normalizedY)
    }
    val eyesOpen = sample.leftEyeOpen > 0.08 && sample.rightEyeOpen > 0.08
    val left = eyeDistance(sample.leftIrisX, sample.leftIrisY, screen.mean.leftIrisX, screen.mean.leftIrisY, screen.deviation.leftIrisX, screen.deviation.leftIrisY)
    val right = eyeDistance(sample.rightIrisX, sample.rightIrisY, screen.mean.rightIrisX, screen.mean.rightIrisY, screen.deviation.rightIrisX, screen.deviation.rightIrisY)
    val screenLooking = headFacesScreen && eyesOpen && left <= 0.9 && right <= 0.9
    return if (screenLooking) AttentionState.SCREEN to 1.0 else AttentionState.NOTEBOOK to 1.0
}

class AttentionStabilizer(
    private val enterNotebookMs: Long = 300,
    private val returnScreenMs: Long = 500,
    private val minimumConfidence: Double = 0.18,
) {
    private var stable = AttentionState.UNKNOWN
    private var candidate = AttentionState.UNKNOWN
    private var candidateSince = 0L

    fun update(raw: AttentionState, confidence: Double, timestamp: Long): AttentionState {
        val floor = if (raw == AttentionState.NOTEBOOK) minimumConfidence * 0.7 else minimumConfidence
        if (raw == AttentionState.UNKNOWN || confidence < floor) return stable
        if (raw == stable) { candidate = AttentionState.UNKNOWN; return stable }
        if (raw != candidate) { candidate = raw; candidateSince = timestamp; return stable }
        val duration = if (raw == AttentionState.NOTEBOOK) enterNotebookMs else returnScreenMs
        if (timestamp - candidateSince >= duration) { stable = raw; candidate = AttentionState.UNKNOWN }
        return stable
    }

    fun loseFace(): AttentionState { stable = AttentionState.NOTEBOOK; candidate = AttentionState.UNKNOWN; return stable }
    fun reset() { stable = AttentionState.UNKNOWN; candidate = AttentionState.UNKNOWN; candidateSince = 0L }
}

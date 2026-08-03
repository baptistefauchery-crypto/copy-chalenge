package com.baptiste.dicta.beta.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionVisionTest {
    private val screenFeatures = AttentionFeatures(
        headPitch = 0.2,
        headYaw = 0.0,
        leftIrisX = 0.5,
        leftIrisY = 0.5,
        rightIrisX = 0.5,
        rightIrisY = 0.5,
        leftEyeOpen = 0.2,
        rightEyeOpen = 0.2,
    )

    @Test
    fun stabilizerUsesStableSiteTimings() {
        val stabilizer = AttentionStabilizer()

        assertEquals(AttentionState.UNKNOWN, stabilizer.update(AttentionState.SCREEN, 1.0, 0L))
        assertEquals(AttentionState.UNKNOWN, stabilizer.update(AttentionState.SCREEN, 1.0, 699L))
        assertEquals(AttentionState.SCREEN, stabilizer.update(AttentionState.SCREEN, 1.0, 700L))

        assertEquals(AttentionState.SCREEN, stabilizer.update(AttentionState.NOTEBOOK, 1.0, 1_000L))
        assertEquals(AttentionState.SCREEN, stabilizer.update(AttentionState.NOTEBOOK, 1.0, 1_219L))
        assertEquals(AttentionState.NOTEBOOK, stabilizer.update(AttentionState.NOTEBOOK, 1.0, 1_220L))
    }

    @Test
    fun missingFaceFailsClosedImmediately() {
        assertEquals(AttentionState.NOTEBOOK, AttentionStabilizer().loseFace())
    }

    @Test
    fun calibrationNeedsAtLeastFiveSamplesAndClassifiesScreenReference() {
        val tooShort = List(4) { screenFeatures }
        var rejected = false
        try {
            summarizeSamples(tooShort)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)

        val calibration = AttentionCalibration(summarizeSamples(List(5) { screenFeatures }))
        assertEquals(AttentionState.SCREEN, classifyFeatures(screenFeatures, calibration).first)
        assertEquals(
            AttentionState.NOTEBOOK,
            classifyFeatures(screenFeatures.copy(headPitch = screenFeatures.headPitch + 0.2), calibration).first,
        )
    }

    @Test
    fun frameLimiterCapsAnalysisAtTenFramesPerSecond() {
        assertTrue(shouldAnalyzeFrame(null, 1_000L))
        assertFalse(shouldAnalyzeFrame(1_000L, 1_099L))
        assertTrue(shouldAnalyzeFrame(1_000L, 1_100L))
    }

    @Test
    fun yuvConversionSkipsRowAndPixelPadding() {
        val result = yuv420ToNv21(
            width = 4,
            height = 2,
            yPlane = YuvPlane(byteArrayOf(10, 11, 12, 13, 99, 99, 20, 21, 22, 23, 88, 88), 6, 1),
            uPlane = YuvPlane(byteArrayOf(1, 99, 2, 99), 4, 2),
            vPlane = YuvPlane(byteArrayOf(3, 99, 4, 99), 4, 2),
        )

        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 20, 21, 22, 23, 3, 1, 4, 2), result)
    }
}

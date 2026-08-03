package com.baptiste.dicta.beta

import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.ScoreOptions
import com.baptiste.dicta.beta.domain.SessionEvent
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.calculateScore
import com.baptiste.dicta.beta.domain.createExercise
import com.baptiste.dicta.beta.domain.createSession
import com.baptiste.dicta.beta.domain.reduceSession
import com.baptiste.dicta.beta.domain.rewardFor
import com.baptiste.dicta.beta.domain.splitTextIntoFragments
import com.baptiste.dicta.beta.ocr.OcrResult
import com.baptiste.dicta.beta.ocr.ComparisonStatus
import com.baptiste.dicta.beta.ocr.compareOcrToReference
import com.baptiste.dicta.beta.ocr.normalizeOcr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAndOcrTest {
    @Test
    fun fragmentsPreserveWordsAndStayWithinLetterBudget() {
        val fragments = splitTextIntoFragments("Un petit chat dort. Il rêve dans la maison.", maxLetters = 12)
        assertTrue(fragments.isNotEmpty())
        assertTrue(fragments.all { it.isNotBlank() })
        assertTrue(fragments.all { it.count(Char::isLetter) <= 12 || it == "rêve" })
        assertEquals("Un petit chat dort. Il rêve dans la maison.", fragments.joinToString(" "))
    }

    @Test
    fun sessionReducerKeepsCameraAndTransitionsThroughChallenge() {
        val exercise = createExercise(SchoolLevel.CP, maxLetters = 80)
        var state = createSession(exercise, "test", DetectionMode.CAMERA)
        state = reduceSession(state, SessionEvent.BeginCalibration)
        state = reduceSession(state, SessionEvent.CalibrationCompleted)
        state = reduceSession(state, SessionEvent.Start(100L))
        state = reduceSession(state, SessionEvent.LookedAway)
        state = reduceSession(state, SessionEvent.Review)
        assertEquals(SessionPhase.MEMORIZING, state.phase)
        assertEquals(1, state.totalReviews)
        assertEquals(DetectionMode.CAMERA, state.detectionMode)
    }

    @Test
    fun scoreAndRewardsAreBoundedAndReviewPenaltyApplies() {
        val options = ScoreOptions(0, 10, 1.0, 0.2)
        val noReview = calculateScore("un texte assez long", 10_000L, 0, options)
        val reviewed = calculateScore("un texte assez long", 10_000L, 1, options)
        assertTrue(noReview in 0..100)
        assertTrue(reviewed < noReview)
        assertEquals(3, rewardFor(60).stars)
        assertEquals("TROPHY", rewardFor(100).badge.name)
    }

    @Test
    fun ocrComparisonNormalizesCaseAndReportsDifferences() {
        assertEquals("l'école", normalizeOcr("  L\u2019ÉCOLE  "))
        val exact = compareOcrToReference("L'école", OcrResult("l'école", 0.9))
        assertTrue(exact.matches)
        assertEquals(ComparisonStatus.MATCH, exact.status)
        val mismatch = compareOcrToReference("L'école", OcrResult("La classe", 0.9))
        assertFalse(mismatch.matches)
        assertEquals(2, mismatch.differences.size)
    }
}

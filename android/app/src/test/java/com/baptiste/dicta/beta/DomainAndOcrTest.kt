package com.baptiste.dicta.beta

import com.baptiste.dicta.beta.data.INITIAL_CURSORS
import com.baptiste.dicta.beta.data.LeaderboardEntry
import com.baptiste.dicta.beta.data.StoredProgress
import com.baptiste.dicta.beta.data.decodeLeaderboard
import com.baptiste.dicta.beta.data.encodeLeaderboard
import com.baptiste.dicta.beta.data.normalizeProgress
import com.baptiste.dicta.beta.data.sortLeaderboard
import com.baptiste.dicta.beta.domain.Badge
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.SessionEvent
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.ScoreOptions
import com.baptiste.dicta.beta.domain.calculateScore
import com.baptiste.dicta.beta.domain.createExercise
import com.baptiste.dicta.beta.domain.createSession
import com.baptiste.dicta.beta.domain.reduceSession
import com.baptiste.dicta.beta.domain.rewardFor
import com.baptiste.dicta.beta.domain.splitTextIntoFragments
import com.baptiste.dicta.beta.ocr.ComparisonStatus
import com.baptiste.dicta.beta.ocr.OcrResult
import com.baptiste.dicta.beta.ocr.compareOcrToReference
import com.baptiste.dicta.beta.ocr.normalizeOcr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAndOcrTest {
    @Test
    fun stableCorpusAndLabelsMatchVersion52() {
        assertEquals(
            listOf(
                "CP — Cours préparatoire",
                "CE1 — Cours élémentaire 1re année",
                "CE2 — Cours élémentaire 2e année",
                "CM1 — Cours moyen 1re année",
                "CM2 — Cours moyen 2e année",
            ),
            SchoolLevel.values().map { it.label },
        )
        assertEquals(listOf(6, 8, 10, 12, 14), SchoolLevel.values().map { it.recommendedLetters })
        assertEquals(15, SchoolLevel.values().sumOf { it.texts.size })
        assertEquals(
            listOf(
                "Lina a un vélo. Elle roule dans la cour.",
                "Le chat dort sur le tapis. Il rêve d’une souris.",
                "Milo joue avec sa balle. Puis il rentre à la maison.",
                "Les petits lapins mangent des carottes dans le jardin.",
                "Ce matin, Zoé prépare son cartable et cherche ses crayons.",
                "La pluie tombe, mais les enfants jouent sous le préau.",
                "Hier, les élèves ont planté des graines près de l’école.",
                "Le vieux bateau avance lentement entre les rochers.",
                "Demain, nous visiterons le musée avec notre classe.",
                "Quand le vent se lève, les grandes branches bougent et les oiseaux s’envolent.",
                "L’année dernière, nous avons découvert un sentier qui longeait la rivière.",
                "Mes cousins sont partis tôt, mais ils ont oublié leurs gourdes.",
                "Après la pluie, les chemins glissants que nous avions suivis brillaient sous les éclaircies.",
                "Si tu prends le temps de relire tes phrases, tu repéreras les accords oubliés.",
                "Les exploratrices avaient préparé leurs sacs avant de partir vers les montagnes enneigées.",
            ),
            SchoolLevel.values().flatMap { it.texts },
        )
    }

    @Test
    fun splitterReachesTargetAtWordBoundaryAndNeverCrossesSentence() {
        assertEquals(
            listOf("Un petit chat dort.", "Il rêve dans la", "maison."),
            splitTextIntoFragments("  Un   petit chat dort.  Il rêve dans la maison. ", maxLetters = 12),
        )
        assertEquals(
            listOf("Alpha beta.", "Gamma delta."),
            splitTextIntoFragments("Alpha beta. Gamma delta.", maxLetters = 100),
        )
        assertEquals(listOf("extraordinaire"), splitTextIntoFragments("extraordinaire", maxLetters = 4))
        assertEquals(emptyList<String>(), splitTextIntoFragments(" \n\t ", maxLetters = 8))
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
    fun scoreMatchesStableFormulaAndIgnoresOcrCompatibilityOptions() {
        assertEquals(80, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 0))
        assertEquals(64, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 1))
        assertEquals(51, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 2))
        assertEquals(100, calculateScore("abcd", elapsedMs = 0L, reviewCount = 0))
        assertEquals(80, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = -3))
        assertEquals(0, calculateScore("123 !", elapsedMs = 1_000L, reviewCount = 0))

        val hostileOcrOptions = ScoreOptions(
            spellingFaults = 999,
            wordCount = 1,
            ocrConfidence = 0.0,
            speedReferenceLettersPerSecond = 999.0,
        )
        assertEquals(80, calculateScore("abcd", 4_000L, 0, hostileOcrOptions))
    }

    @Test
    fun rewardsMatchEveryStableThreshold() {
        val cases = listOf(
            0 to (0 to Badge.NONE),
            19 to (0 to Badge.NONE),
            20 to (1 to Badge.NONE),
            39 to (1 to Badge.NONE),
            40 to (2 to Badge.NONE),
            59 to (2 to Badge.NONE),
            60 to (3 to Badge.NONE),
            69 to (3 to Badge.NONE),
            70 to (3 to Badge.BRONZE),
            79 to (3 to Badge.BRONZE),
            80 to (3 to Badge.SILVER),
            89 to (3 to Badge.SILVER),
            90 to (3 to Badge.GOLD),
            99 to (3 to Badge.GOLD),
            100 to (3 to Badge.TROPHY),
        )
        cases.forEach { (score, expected) ->
            val reward = rewardFor(score)
            assertEquals("stars for $score", expected.first, reward.stars)
            assertEquals("badge for $score", expected.second, reward.badge)
        }
    }

    @Test
    fun progressAndLeaderboardFollowStableLocalStorageRules() {
        val progress = normalizeProgress(
            StoredProgress(
                level = SchoolLevel.CE2,
                index = 7,
                cursors = mapOf(SchoolLevel.CE2 to 4, SchoolLevel.CM1 to -1),
                lettersPerFragment = 140,
            ),
        )
        assertEquals(SchoolLevel.CE2, progress.level)
        assertEquals(7, progress.index)
        assertEquals(100, progress.lettersPerFragment)
        assertEquals(4, progress.cursors.getValue(SchoolLevel.CE2))
        assertEquals(INITIAL_CURSORS.getValue(SchoolLevel.CM1), progress.cursors.getValue(SchoolLevel.CM1))

        val entries = listOf(
            LeaderboardEntry("later-tie", 80, 20),
            LeaderboardEntry("best", 100, 30),
            LeaderboardEntry("earlier-tie", 80, 10),
            LeaderboardEntry("fourth", 70, 40),
            LeaderboardEntry("fifth", 60, 50),
            LeaderboardEntry("sixth", 50, 60),
            LeaderboardEntry("invalid", 101, 1),
        )
        val sorted = sortLeaderboard(entries)
        assertEquals(listOf("best", "earlier-tie", "later-tie", "fourth", "fifth"), sorted.map { it.id })
        assertEquals(sorted, decodeLeaderboard(encodeLeaderboard(entries)))
        assertEquals(42, decodeLeaderboard("42|1234").single().score)
    }

    @Test
    fun ocrComparisonNormalizesCaseAndReportsDifferences() {
        assertEquals("l'école", normalizeOcr("  L’ÉCOLE  "))
        val exact = compareOcrToReference("L'école", OcrResult("l'école", 0.9))
        assertTrue(exact.matches)
        assertEquals(ComparisonStatus.MATCH, exact.status)
        val mismatch = compareOcrToReference("L'école", OcrResult("La classe", 0.9))
        assertFalse(mismatch.matches)
        assertEquals(2, mismatch.differences.size)
    }
}

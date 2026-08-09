package com.baptiste.dicta.beta

import com.baptiste.dicta.beta.data.INITIAL_CURSORS
import com.baptiste.dicta.beta.data.LeaderboardEntry
import com.baptiste.dicta.beta.data.StoredProgress
import com.baptiste.dicta.beta.data.advanceChallengeProgress
import com.baptiste.dicta.beta.data.decodeLeaderboard
import com.baptiste.dicta.beta.data.encodeLeaderboard
import com.baptiste.dicta.beta.data.normalizeProgress
import com.baptiste.dicta.beta.data.sortLeaderboard
import com.baptiste.dicta.beta.domain.Badge
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.FragmentMode
import com.baptiste.dicta.beta.domain.SessionEvent
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.ScoreOptions
import com.baptiste.dicta.beta.domain.calculateScore
import com.baptiste.dicta.beta.domain.calculateScoreBreakdown
import com.baptiste.dicta.beta.domain.createExercise
import com.baptiste.dicta.beta.domain.createSession
import com.baptiste.dicta.beta.domain.reduceSession
import com.baptiste.dicta.beta.domain.rewardFor
import com.baptiste.dicta.beta.domain.splitTextIntoFragmentDetails
import com.baptiste.dicta.beta.domain.splitTextIntoFragments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun corpusIncludesOrderedLaFontaineChallengesAndLevelLabels() {
        assertEquals(
            listOf(
                "CP — Cours préparatoire",
                "CE1 — Cours élémentaire 1re année",
                "Niveau intermédiaire",
                "Niveau avancé",
                "Perfectionnement",
            ),
            SchoolLevel.values().map { it.label },
        )
        assertEquals(listOf(6, 8, 10, 12, 14), SchoolLevel.values().map { it.recommendedLetters })
        assertEquals(15, SchoolLevel.values().sumOf { it.texts.size })
        assertTrue(SchoolLevel.CM1.texts.first().startsWith("La Cigale, ayant chanté\nTout l’été,"))
        assertTrue(SchoolLevel.CM1.texts.first().contains("Chez la Fourmi sa voisine,"))
        assertTrue(SchoolLevel.CM2.texts[0].startsWith("Maître Corbeau,"))
        assertTrue(SchoolLevel.CM2.texts[0].contains("Le Renard s’en saisit"))
        assertTrue(SchoolLevel.CM2.texts[1].startsWith("La raison du plus fort"))
        assertTrue(SchoolLevel.CM2.texts[1].contains("Le Loup l’emporte"))
        assertEquals(FragmentMode.LETTERS, SchoolLevel.CM1.dictations.first().fragmentMode)
        assertEquals(FragmentMode.VERSES, SchoolLevel.CM2.dictations[0].fragmentMode)
        assertEquals(FragmentMode.VERSES, SchoolLevel.CM2.dictations[1].fragmentMode)
    }

    @Test
    fun splitterReachesTargetAtWordBoundaryAndNeverCrossesSentence() {
        assertEquals(
            listOf("Un petit chat dort.", "Il rêve dans la maison."),
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
    fun splitterAlwaysKeepsRequestedDeterminersWithFollowingWord() {
        val source = "Voir le grand Corbeau et la Fourmi avec un Renard, une Cigale, des graines, les bois, au matin et aux champs."
        val fragments = splitTextIntoFragments(source, maxLetters = 4)
        val forbiddenEnd = Regex("(?:^|\\s)(?:le|la|l[’']|un|une|des|les|au|aux)$", RegexOption.IGNORE_CASE)

        assertTrue(fragments.size > 2)
        assertTrue(fragments.none { forbiddenEnd.containsMatchIn(it) })
        assertEquals(source, fragments.joinToString(" "))
    }

    @Test
    fun perfectionnementUsesOneFragmentPerVerseAndExposesVerseEnds() {
        val exercise = createExercise(SchoolLevel.CM2, maxLetters = 1, index = 0)
        val sourceLines = exercise.sourceText.lines().filter(String::isNotBlank)

        assertEquals(sourceLines.size, exercise.fragments.size)
        assertEquals(
            listOf(
                "Maître Corbeau, sur un arbre perché,",
                "Tenait en son bec un fromage.",
                "Maître Renard, par l’odeur alléché,",
            ),
            exercise.fragments.take(3),
        )
        assertTrue(exercise.fragmentDetails.all { it.endsVerse })
    }

    @Test
    fun advancedPoemKeepsVerseBoundaryMetadataWithLetterFragments() {
        val definition = SchoolLevel.CM1.dictations.first()
        val details = splitTextIntoFragmentDetails(
            definition.text,
            maxLetters = 8,
            mode = definition.fragmentMode,
            preserveVerseBreaks = definition.preserveVerseBreaks,
        )

        assertEquals("La Cigale,", details[0].text)
        assertFalse(details[0].endsVerse)
        assertEquals("ayant chanté", details[1].text)
        assertTrue(details[1].endsVerse)
        assertEquals("Tout l’été,", details[2].text)
        assertTrue(details[2].endsVerse)
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
    fun scoreUsesTheSameLettersTimeAndReviewsFormulaAsTheWebDomain() {
        val perfect = ScoreOptions(
            speedReferenceLettersPerSecond = 2.0,
        )

        assertEquals(100, calculateScore("Le chat dort", 5_000L, 0, perfect))
        assertEquals(64, calculateScore("Le chat dort", 10_000L, 1, perfect))
        assertEquals(80, calculateScore("Le chat dort", 10_000L, 0, perfect))
        assertEquals(0, calculateScore("123 !", 1_000L, 0, perfect))

        assertEquals(80, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 0))
        assertEquals(64, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 1))
        assertEquals(51, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = 2))
        assertEquals(100, calculateScore("abcd", elapsedMs = 0L, reviewCount = 0))
        assertEquals(80, calculateScore("abcd", elapsedMs = 4_000L, reviewCount = -3))
        assertEquals(0, calculateScore("123 !", elapsedMs = 1_000L, reviewCount = 0))
    }

    @Test
    fun rawScoreCanExceedOneHundredBeforeTheDisplayCap() {
        val result = calculateScoreBreakdown(
            text = "a".repeat(250),
            elapsedMs = 100_000L,
            options = ScoreOptions(
                speedReferenceLettersPerSecond = 2.0,
            ),
        )

        assertEquals(200.0, result.rawScore, 0.000_001)
        assertEquals(100, result.score)
        assertEquals(200.0, result.baseScore, 0.0)
        assertEquals(0.0, result.lengthBonus, 0.0)
        assertEquals(0.0, result.speedAdjustment, 0.0)
    }

    @Test
    fun scoreCanReachOneHundredWithoutPerfectSpeedOrReadability() {
        val result = calculateScoreBreakdown(
            text = "abcdefghij",
            elapsedMs = 8_000L,
            options = ScoreOptions(
                speedReferenceLettersPerSecond = 2.0,
            ),
        )

        assertEquals(100.0, result.rawScore, 0.001)
        assertEquals(100, result.score)
    }

    @Test
    fun reviewsApplyACompoundingPenalty() {
        val text = "a".repeat(50)
        val options = ScoreOptions(
            speedReferenceLettersPerSecond = 2.0,
        )
        val baseline = calculateScoreBreakdown(text, 50_000L, 0, options)
        val oneReview = calculateScoreBreakdown(text, 50_000L, 1, options)
        val twoReviews = calculateScoreBreakdown(text, 50_000L, 2, options)
        val slowest = calculateScoreBreakdown(text, Long.MAX_VALUE, 0, options)

        assertEquals(80.0, baseline.rawScore, 0.001)
        assertTrue(baseline.rawScore > oneReview.rawScore)
        assertTrue(baseline.rawScore > slowest.rawScore)
        assertEquals(0.8, oneReview.reviewMultiplier, 0.0)
        assertEquals(64.0, oneReview.rawScore, 0.000_001)
        assertEquals(51.2, twoReviews.rawScore, 0.000_001)
    }

    @Test
    fun malformedFloatingPointInputsStayFiniteAndBounded() {
        val result = calculateScoreBreakdown(
            text = "abcdefghij",
            elapsedMs = 1_000L,
            reviewCount = -4,
            options = ScoreOptions(
                speedReferenceLettersPerSecond = Double.NaN,
            ),
        )

        assertTrue(result.rawScore.isFinite())
        assertTrue(result.score in 0..100)
        assertEquals(1.0, result.reviewMultiplier, 0.0)
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
    fun challengeCounterAdvancesIndexAndCursorLikeTheWeb() {
        val first = StoredProgress(
            level = SchoolLevel.CP,
            index = 0,
            cursors = INITIAL_CURSORS,
            lettersPerFragment = 100,
        )

        val second = advanceChallengeProgress(first)
        assertEquals(1, second.index)
        assertEquals(2, second.cursors.getValue(SchoolLevel.CP))
        assertEquals(SchoolLevel.CP.recommendedLetters, second.lettersPerFragment)

        val wrapped = advanceChallengeProgress(second.copy(index = 2))
        assertEquals(0, wrapped.index)
        assertEquals(1, wrapped.cursors.getValue(SchoolLevel.CP))
    }

}

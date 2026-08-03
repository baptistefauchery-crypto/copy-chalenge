package com.baptiste.dicta.beta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the visible stable-v52 contract against accidental beta/OCR regressions. */
class StableCloneStructureTest {
    private val sourceRoot = File("src/main/java/com/baptiste/dicta/beta")

    @Test
    fun composeFlowKeepsStableScreensCopyAndRewardTiming() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()

        listOf(
            "Copy Challenge",
            "Lancer un challenge.",
            "Continuer sans caméra",
            "Place ton visage dans le repère.",
            "Lis le challenge",
            "J’ai lu",
            "Mémorise ces mots, puis écris-les sur ton cahier.",
            "Revoir les mots ?",
            "Bravo, c’est terminé !",
            "Préparer le challenge suivant",
            "SCORE_REVEAL_DURATION_MS = 1_800",
            "REWARD_FEATURE_DURATION_MS = 2_400L",
            "if (score > 80) playScoreFanfare()",
        ).forEach { expected -> assertTrue("Missing stable UI contract: $expected", ui.contains(expected)) }

        assertFalse(ui.contains("OcrScreen"))
        assertFalse(ui.contains("Photographiez votre feuille"))
        assertFalse(ui.contains("bêta copy chalenge"))
    }

    @Test
    fun homeBannerUsesTheWebSiteAsset() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()
        val banner = File("src/main/res/drawable-nodpi/dicta_banner_tilted_notebook.png")

        assertTrue(ui.contains("painterResource(R.drawable.dicta_banner_tilted_notebook)"))
        assertTrue("Missing shared website banner asset", banner.isFile)
    }

    @Test
    fun summaryConfettiMatchesTheWebCelebrationLayer() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()

        listOf(
            "if (revealComplete && score > 0) ConfettiField()",
            "private fun BoxScope.ConfettiField()",
            "Modifier.matchParentSize()",
            "index % 6 == 0 -> \"left\"",
            "index % 6 == 1 -> \"right\"",
            "CONFETTI_DURATION_MS = 6_500",
            "CONFETTI_MAX_DELAY_MS = 4_140",
            "rotation - movement * 540f",
        ).forEach { expected -> assertTrue("Missing web confetti contract: $expected", ui.contains(expected)) }
    }

    @Test
    fun viewModelUsesWorkingCalibrationDirectScoreAndFailClosedTiming() {
        val viewModel = sourceRoot.resolve("DictaViewModel.kt").readText()

        listOf(
            "SessionEvent.BeginCalibration",
            "SessionEvent.CalibrationCompleted",
            "SessionEvent.Start(now)",
            "calculateScore(session.exercise.sourceText, elapsed, session.totalReviews)",
            "now - lastCameraReadingAt > 1_200L",
            "autoHideBlockedUntil = now + 2_000L",
            "!reading.faceDetected || reading.state == AttentionState.NOTEBOOK",
        ).forEach { expected -> assertTrue("Missing stable flow contract: $expected", viewModel.contains(expected)) }

        assertFalse(viewModel.contains("OnnxOcrEngine"))
        assertFalse(viewModel.contains("startOcr"))
        assertFalse(viewModel.contains("finishWithoutOcr"))
    }

    @Test
    fun updateCheckAlwaysShowsAResult() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()
        val checker = sourceRoot.resolve("update/GitHubReleaseUpdateChecker.kt").readText()

        assertTrue(ui.contains("L’application est à jour (\${BuildConfig.VERSION_NAME})."))
        assertTrue(ui.contains("state.updateCheckMessage ?: \"Impossible de joindre GitHub"))
        assertTrue(ui.contains("enabled = state.updateCheckState != UpdateCheckState.CHECKING"))
        assertTrue(checker.contains("GitHub releases request failed with HTTP"))
    }
}

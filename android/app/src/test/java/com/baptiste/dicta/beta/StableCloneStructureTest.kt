package com.baptiste.dicta.beta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the finished Android beta flow. */
class StableCloneStructureTest {
    private val sourceRoot = File("src/main/java/com/baptiste/dicta/beta")

    @Test
    fun composeFlowKeepsStableScreensCopyAndRewardTiming() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText().replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

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

        assertFalse(ui.contains("Autoriser la caméra"))
        assertFalse(ui.contains("bêta copy chalenge"))
        assertFalse(ui.contains("PP-OCRv6"))
        assertFalse(ui.contains("effet miroir"))
        assertFalse(ui.contains("Photographie ton texte"))
        assertFalse(ui.contains("CameraMode.BACK"))
        assertFalse(ui.contains("spelling"))
    }

    @Test
    fun homeBannerUsesTheWebSiteAsset() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()
        val banner = File("src/main/res/drawable-nodpi/dicta_banner_tilted_notebook.png")

        assertTrue(ui.contains("painterResource(R.drawable.dicta_banner_tilted_notebook)"))
        assertTrue("Missing shared website banner asset", banner.isFile)
    }

    @Test
    fun challengeCounterKeepsItsClickActionAndReadableBalancedShape() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()

        listOf(
            "Pill(\"Challenge \${state.challengeIndex + 1} sur \${state.challengeTotal}\", vm::advanceChallenge)",
            ".defaultMinSize(minWidth = 156.dp, minHeight = 52.dp)",
            "shape = RoundedCornerShape(16.dp)",
            "fontSize = 14.sp",
            "fontWeight = FontWeight.Black",
            "contentDescription = \"Passer au challenge suivant\"",
        ).forEach { expected -> assertTrue("Missing readable challenge counter contract: $expected", ui.contains(expected)) }
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
    fun viewModelUsesWorkingCalibrationScoreAndConservativeGazeTiming() {
        val viewModel = sourceRoot.resolve("DictaViewModel.kt").readText()

        listOf(
            "SessionEvent.BeginCalibration",
            "SessionEvent.CalibrationCompleted",
            "SessionEvent.Start(now)",
            "screen = AppScreen.SUMMARY",
            "reviewCount = session.totalReviews",
            "!reading.faceDetected || reading.state == AttentionState.NOTEBOOK",
            "now - lastCameraReadingAt > 1_200L",
            "autoHideBlockedUntil = now + 2_000L",
            "reduceSession(session, SessionEvent.LookedAway)",
        ).forEach { expected -> assertTrue("Missing stable flow contract: $expected", viewModel.contains(expected)) }

        assertFalse(viewModel.contains("OnnxOcrEngine"))
        assertFalse(viewModel.contains("finishWithoutOcr"))
    }

    @Test
    fun updatesStayInHelpAndPlacementKeepsManualModeAboveTheFold() {
        val ui = sourceRoot.resolve("DictaApp.kt").readText()
        val setup = ui.substringAfter("private fun SetupScreen").substringBefore("private fun HelpPanel")
        val help = ui.substringAfter("private fun HelpPanel").substringBefore("private fun HomeIllustration")
        val placement = ui.substringAfter("private fun PlacementScreen").substringBefore("private fun CameraStage")
        val cameraStage = ui.substringAfter("private fun CameraStage").substringBefore("private fun CalibrationScreen")

        assertTrue(setup.contains("HelpPanel("))
        assertFalse(setup.contains("\"Rechercher les mises à jour\""))
        assertFalse(setup.contains("\"L’application est à jour"))
        assertTrue(help.contains("UpdateSection(state, onCheckUpdates, onDownloadUpdate, onInstallUpdate, installPermissionMessage)"))
        assertTrue(help.contains("\"Rechercher les mises à jour\""))
        assertTrue(help.contains("\"L’application est à jour"))
        assertTrue(help.contains("\"Télécharger la mise à jour\""))

        assertFalse(placement.contains("Installation"))
        assertTrue(placement.contains("fontSize = 36.sp, lineHeight = 38.sp"))
        assertTrue(placement.contains("Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(placement.contains("Utiliser le mode manuel"))
        assertTrue(cameraStage.contains(".height(PLACEMENT_CAMERA_HEIGHT)"))
        assertFalse(cameraStage.contains(".aspectRatio(.84f)"))
        assertTrue(ui.contains("private val PLACEMENT_CAMERA_HEIGHT = 280.dp"))
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

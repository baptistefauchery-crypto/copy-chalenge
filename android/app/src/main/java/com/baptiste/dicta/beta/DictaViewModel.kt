package com.baptiste.dicta.beta

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baptiste.dicta.beta.data.LeaderboardEntry
import com.baptiste.dicta.beta.data.LocalStore
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.Exercise
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.ScoreOptions
import com.baptiste.dicta.beta.domain.SessionEvent
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.domain.SessionState
import com.baptiste.dicta.beta.domain.calculateScore
import com.baptiste.dicta.beta.domain.countScoringWords
import com.baptiste.dicta.beta.domain.createExercise
import com.baptiste.dicta.beta.domain.createSession
import com.baptiste.dicta.beta.domain.reduceSession
import com.baptiste.dicta.beta.domain.rewardFor
import com.baptiste.dicta.beta.ocr.FrenchSpellChecker
import com.baptiste.dicta.beta.ocr.OcrComparison
import com.baptiste.dicta.beta.ocr.OcrResult
import com.baptiste.dicta.beta.ocr.OnnxOcrEngine
import com.baptiste.dicta.beta.ocr.SpellingResult
import com.baptiste.dicta.beta.ocr.compareOcrToReference
import com.baptiste.dicta.beta.vision.AttentionReading
import com.baptiste.dicta.beta.vision.AttentionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen { SETUP, PLACEMENT, READY, SESSION, SUMMARY, OCR, ERROR }

data class DictaUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val level: SchoolLevel = SchoolLevel.CP,
    val maxLetters: Int = SchoolLevel.CP.recommendedLetters,
    val session: SessionState? = null,
    val attention: AttentionState = AttentionState.UNKNOWN,
    val faceDetected: Boolean = false,
    val calibrationMessage: String? = null,
    val hasSeenScreen: Boolean = false,
    val score: Int? = null,
    val rewardStars: Int = 0,
    val rewardBadge: String = "",
    val ocrResult: OcrResult? = null,
    val comparison: OcrComparison? = null,
    val spelling: SpellingResult? = null,
    val isOcrProcessing: Boolean = false,
    val ocrError: String? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val error: String? = null,
)

class DictaViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val ocrEngine = OnnxOcrEngine(application.assets)
    private val spellChecker = FrenchSpellChecker(application)
    private val state = MutableStateFlow(DictaUiState(leaderboard = store.readLeaderboard()))
    val uiState: StateFlow<DictaUiState> = state.asStateFlow()
    private var hasSeenScreen = false

    fun selectLevel(level: SchoolLevel) {
        state.value = state.value.copy(level = level, maxLetters = level.recommendedLetters)
    }

    fun setMaxLetters(value: Int) { state.value = state.value.copy(maxLetters = value.coerceIn(4, 32)) }

    fun startChallenge() {
        val current = state.value
        val exercise = createExercise(current.level, current.maxLetters)
        val session = createSession(exercise, "session-${System.currentTimeMillis()}")
        hasSeenScreen = false
        state.value = current.copy(screen = AppScreen.PLACEMENT, session = session, score = null, calibrationMessage = null, ocrError = null)
    }

    fun useManualMode() {
        state.value.session?.let { session ->
            val updated = reduceSession(session, SessionEvent.UseManualMode)
            state.value = state.value.copy(session = updated, screen = if (updated.phase == SessionPhase.READY) AppScreen.READY else AppScreen.PLACEMENT)
        }
    }

    fun calibrationFinished(success: Boolean) {
        if (!success) {
            state.value = state.value.copy(calibrationMessage = "Je n’ai pas trouvé un visage stable. Rapprochez le téléphone et réessayez.")
            return
        }
        state.value.session?.let { session ->
            val updated = reduceSession(session, SessionEvent.CalibrationCompleted)
            state.value = state.value.copy(screen = AppScreen.READY, session = updated, calibrationMessage = "Calibration réussie.")
        }
    }

    fun startSession() {
        val session = state.value.session ?: return
        hasSeenScreen = false
        state.value = state.value.copy(
            screen = AppScreen.SESSION,
            hasSeenScreen = false,
            attention = AttentionState.UNKNOWN,
            faceDetected = false,
            session = reduceSession(session, SessionEvent.Start(System.currentTimeMillis())),
        )
    }

    fun onAttention(reading: AttentionReading) {
        val current = state.value
        val session = current.session ?: return
        if (reading.state == AttentionState.SCREEN && reading.faceDetected) hasSeenScreen = true
        var updated = session
        if (session.phase == SessionPhase.MEMORIZING && session.detectionMode == DetectionMode.CAMERA && reading.state == AttentionState.NOTEBOOK && hasSeenScreen) {
            updated = reduceSession(session, SessionEvent.LookedAway)
            hasSeenScreen = false
        }
        state.value = current.copy(
            session = updated,
            attention = reading.state,
            faceDetected = reading.faceDetected,
            hasSeenScreen = hasSeenScreen,
        )
    }

    fun lookedAwayManually() {
        val session = state.value.session ?: return
        if (session.phase == SessionPhase.MEMORIZING) {
            hasSeenScreen = false
            state.value = state.value.copy(session = reduceSession(session, SessionEvent.LookedAway), hasSeenScreen = false)
        }
    }

    fun review() {
        state.value.session?.let { session ->
            hasSeenScreen = false
            state.value = state.value.copy(session = reduceSession(session, SessionEvent.Review), hasSeenScreen = false)
        }
    }

    fun continueFragment() {
        val session = state.value.session ?: return
        val updated = reduceSession(session, SessionEvent.Continue(System.currentTimeMillis()))
        hasSeenScreen = false
        if (updated.phase == SessionPhase.SUMMARY) {
            state.value = state.value.copy(screen = AppScreen.SUMMARY, session = updated, hasSeenScreen = false)
        } else state.value = state.value.copy(session = updated, hasSeenScreen = false)
    }

    fun startOcr() { state.value = state.value.copy(screen = AppScreen.OCR, ocrError = null) }

    fun processOcr(bitmap: Bitmap) {
        val session = state.value.session ?: return
        state.value = state.value.copy(isOcrProcessing = true, ocrError = null)
        viewModelScope.launch {
            try {
                val result = ocrEngine.recognize(bitmap)
                val comparison = compareOcrToReference(session.exercise.sourceText, result)
                val spelling = spellChecker.check(result.text, session.exercise.sourceText)
                val score = scoreFor(session, result, comparison, spelling)
                store.saveScore(score)
                state.value = state.value.copy(
                    screen = AppScreen.SUMMARY,
                    score = score,
                    rewardStars = rewardFor(score).stars,
                    rewardBadge = rewardFor(score).badge.name,
                    ocrResult = result,
                    comparison = comparison,
                    spelling = spelling,
                    isOcrProcessing = false,
                    leaderboard = store.readLeaderboard(),
                )
            } catch (error: Throwable) {
                state.value = state.value.copy(isOcrProcessing = false, ocrError = error.message ?: "La vérification est indisponible.")
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun finishWithoutOcr() {
        val session = state.value.session ?: return
        val result = OcrResult("", 0.0)
        val comparison = compareOcrToReference(session.exercise.sourceText, result)
        val spelling = SpellingResult("", 0, emptyList())
        val score = scoreFor(session, result, comparison, spelling)
        store.saveScore(score)
        state.value = state.value.copy(screen = AppScreen.SUMMARY, score = score, rewardStars = rewardFor(score).stars, rewardBadge = rewardFor(score).badge.name, ocrResult = result, comparison = comparison, spelling = spelling, leaderboard = store.readLeaderboard())
    }

    fun reset() {
        hasSeenScreen = false
        state.value = DictaUiState(level = state.value.level, maxLetters = state.value.level.recommendedLetters, leaderboard = store.readLeaderboard())
    }

    fun fail(message: String) {
        state.value = state.value.copy(screen = AppScreen.ERROR, error = message)
    }

    private fun scoreFor(session: SessionState, result: OcrResult, comparison: OcrComparison, spelling: SpellingResult): Int {
        val elapsed = ((session.completedAt ?: System.currentTimeMillis()) - (session.startedAt ?: System.currentTimeMillis())).coerceAtLeast(1000L)
        val faults = comparison.differences.size + spelling.issues.size
        return calculateScore(
            session.exercise.sourceText,
            elapsed,
            session.totalReviews,
            ScoreOptions(faults, countScoringWords(session.exercise.sourceText), result.confidence, session.exercise.level.referenceLettersPerSecond),
        )
    }

    override fun onCleared() { ocrEngine.close() }
}

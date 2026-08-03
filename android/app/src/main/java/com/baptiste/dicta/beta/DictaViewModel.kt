package com.baptiste.dicta.beta

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baptiste.dicta.beta.data.INITIAL_CURSORS
import com.baptiste.dicta.beta.data.LeaderboardEntry
import com.baptiste.dicta.beta.data.LocalStore
import com.baptiste.dicta.beta.data.StoredProgress
import com.baptiste.dicta.beta.data.advanceChallengeProgress
import com.baptiste.dicta.beta.domain.DetectionMode
import com.baptiste.dicta.beta.domain.SchoolLevel
import com.baptiste.dicta.beta.domain.SessionEvent
import com.baptiste.dicta.beta.domain.SessionPhase
import com.baptiste.dicta.beta.domain.SessionState
import com.baptiste.dicta.beta.domain.calculateScore
import com.baptiste.dicta.beta.domain.createExercise
import com.baptiste.dicta.beta.domain.createSession
import com.baptiste.dicta.beta.domain.reduceSession
import com.baptiste.dicta.beta.vision.AttentionReading
import com.baptiste.dicta.beta.vision.AttentionState
import com.baptiste.dicta.beta.update.AppUpdate
import com.baptiste.dicta.beta.update.GitHubReleaseUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen { SETUP, PLACEMENT, CALIBRATION, SESSION, SUMMARY, ERROR }

enum class CalibrationStage { PREPARING, MEASURING, READY, FAILED }
enum class UpdateCheckState { IDLE, CHECKING, UP_TO_DATE, FAILED }

data class DictaUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val level: SchoolLevel = SchoolLevel.CP,
    val challengeIndex: Int = 0,
    val challengeTotal: Int = SchoolLevel.CP.texts.size,
    val maxLetters: Int = SchoolLevel.CP.recommendedLetters,
    val wordCount: Int = SchoolLevel.CP.texts.first().trim().split(Regex("\\s+")).size,
    val session: SessionState? = null,
    val attention: AttentionState = AttentionState.UNKNOWN,
    val faceDetected: Boolean = false,
    val calibrationStage: CalibrationStage = CalibrationStage.PREPARING,
    val calibrationAttempt: Int = 0,
    val calibrationReadConfirmed: Boolean = false,
    val score: Int? = null,
    val currentScoreId: String? = null,
    val isNewBestScore: Boolean = false,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val cameraMessage: String? = null,
    val availableUpdate: AppUpdate? = null,
    val updateCheckState: UpdateCheckState = UpdateCheckState.IDLE,
    val error: String? = null,
)

class DictaViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private var progress = runCatching { store.readProgress() }
        .getOrElse {
            StoredProgress(
                level = SchoolLevel.CP,
                index = 0,
                cursors = INITIAL_CURSORS,
                lettersPerFragment = SchoolLevel.CP.recommendedLetters,
            )
        }
    private var autoHideBlockedUntil = 0L
    private var lastCameraReadingAt = 0L
    private val updateChecker = GitHubReleaseUpdateChecker()
    private var lastUpdateCheckAt = 0L
    private var latestUpdate: AppUpdate? = null
    private var latestUpdateCheckState = UpdateCheckState.IDLE

    private val state = MutableStateFlow(stateFromProgress(progress))
    val uiState: StateFlow<DictaUiState> = state.asStateFlow()

    init {
        checkForUpdates(force = true)
        viewModelScope.launch {
            while (true) {
                delay(250)
                enforceCameraWatchdog()
            }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastUpdateCheckAt < 15 * 60 * 1_000L) return
        if (state.value.updateCheckState == UpdateCheckState.CHECKING) return
        lastUpdateCheckAt = now
        latestUpdateCheckState = UpdateCheckState.CHECKING
        state.value = state.value.copy(updateCheckState = latestUpdateCheckState)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { updateChecker.check(BuildConfig.VERSION_NAME) }
                .onSuccess { update ->
                    latestUpdate = update
                    latestUpdateCheckState = UpdateCheckState.UP_TO_DATE
                    state.value = state.value.copy(availableUpdate = latestUpdate, updateCheckState = latestUpdateCheckState)
                }
                .onFailure {
                    latestUpdateCheckState = UpdateCheckState.FAILED
                    state.value = state.value.copy(updateCheckState = latestUpdateCheckState)
                }
        }
    }

    fun selectLevel(level: SchoolLevel) {
        val index = normalizedIndex(level, progress.cursors[level] ?: 0)
        val cursors = progress.cursors.toMutableMap().apply {
            this[level] = (index + 1) % level.texts.size
        }
        progress = StoredProgress(level, index, cursors, level.recommendedLetters)
        persistProgress()
        state.value = stateFromProgress(progress, cameraMessage = state.value.cameraMessage)
    }

    fun advanceChallenge() {
        progress = advanceChallengeProgress(progress)
        persistProgress()
        state.value = stateFromProgress(progress, cameraMessage = state.value.cameraMessage)
    }

    fun setMaxLetters(value: Int) {
        progress = progress.copy(lettersPerFragment = value.coerceIn(1, 100))
        persistProgress()
        state.value = state.value.copy(maxLetters = progress.lettersPerFragment)
    }

    /** Starts the stable camera path: placement, full-text reading/calibration, then fragments. */
    fun startChallenge() {
        val exercise = createSelectedExercise()
        val session = reduceSession(
            createSession(exercise, "session-${System.currentTimeMillis()}", DetectionMode.CAMERA),
            SessionEvent.BeginCalibration,
        )
        state.value = state.value.copy(
            screen = AppScreen.PLACEMENT,
            session = session,
            attention = AttentionState.UNKNOWN,
            faceDetected = false,
            calibrationStage = CalibrationStage.PREPARING,
            calibrationAttempt = 0,
            calibrationReadConfirmed = false,
            score = null,
            currentScoreId = null,
            isNewBestScore = false,
            cameraMessage = null,
            error = null,
        )
    }

    /** Starts the same stable reading/session flow without opening the camera. */
    fun startManualChallenge() {
        val exercise = createSelectedExercise()
        val session = reduceSession(
            createSession(exercise, "session-${System.currentTimeMillis()}", DetectionMode.CAMERA),
            SessionEvent.UseManualMode,
        )
        state.value = state.value.copy(
            screen = AppScreen.CALIBRATION,
            session = session,
            attention = AttentionState.UNKNOWN,
            faceDetected = false,
            calibrationStage = CalibrationStage.READY,
            calibrationAttempt = 0,
            calibrationReadConfirmed = false,
            score = null,
            currentScoreId = null,
            isNewBestScore = false,
            cameraMessage = null,
            error = null,
        )
    }

    fun beginCameraCalibration() {
        val session = state.value.session ?: return
        if (session.phase != SessionPhase.CALIBRATION) return
        state.value = state.value.copy(
            screen = AppScreen.CALIBRATION,
            calibrationStage = CalibrationStage.PREPARING,
            calibrationReadConfirmed = false,
            calibrationAttempt = 0,
        )
    }

    fun calibrationMeasuring() {
        if (state.value.screen == AppScreen.CALIBRATION) {
            state.value = state.value.copy(calibrationStage = CalibrationStage.MEASURING)
        }
    }

    fun calibrationFinished(success: Boolean) {
        val current = state.value
        val session = current.session ?: return
        if (!success) {
            state.value = current.copy(calibrationStage = CalibrationStage.FAILED)
            return
        }
        val ready = reduceSession(session, SessionEvent.CalibrationCompleted)
        state.value = current.copy(session = ready, calibrationStage = CalibrationStage.READY)
        if (current.calibrationReadConfirmed) startSession()
    }

    fun retryCalibration() {
        state.value = state.value.copy(
            calibrationStage = CalibrationStage.PREPARING,
            calibrationReadConfirmed = false,
            calibrationAttempt = state.value.calibrationAttempt + 1,
        )
    }

    fun confirmCalibrationRead() {
        val current = state.value
        if (current.calibrationReadConfirmed && current.calibrationStage != CalibrationStage.READY) return
        state.value = current.copy(calibrationReadConfirmed = true)
        if (current.calibrationStage == CalibrationStage.READY) startSession()
    }

    private fun startSession() {
        val current = state.value
        val session = current.session ?: return
        if (session.phase != SessionPhase.READY) return
        val now = System.currentTimeMillis()
        val started = reduceSession(session, SessionEvent.Start(now))
        beginGracePeriod()
        state.value = current.copy(
            screen = AppScreen.SESSION,
            session = started,
            attention = AttentionState.UNKNOWN,
            faceDetected = false,
            calibrationReadConfirmed = true,
        )
    }

    fun onAttention(reading: AttentionReading) {
        val current = state.value
        val session = current.session
        lastCameraReadingAt = reading.timestamp
        var updated = session
        if (
            current.screen == AppScreen.SESSION &&
            session?.phase == SessionPhase.MEMORIZING &&
            session.detectionMode == DetectionMode.CAMERA &&
            SystemClock.uptimeMillis() >= autoHideBlockedUntil &&
            (!reading.faceDetected || reading.state == AttentionState.NOTEBOOK)
        ) {
            updated = reduceSession(session, SessionEvent.LookedAway)
        }
        state.value = current.copy(
            session = updated,
            attention = reading.state,
            faceDetected = reading.faceDetected,
        )
    }

    fun hideFragment() {
        val session = state.value.session ?: return
        if (session.phase == SessionPhase.MEMORIZING) {
            state.value = state.value.copy(session = reduceSession(session, SessionEvent.LookedAway))
        }
    }

    fun review() {
        val session = state.value.session ?: return
        if (session.phase != SessionPhase.DECISION) return
        beginGracePeriod()
        state.value = state.value.copy(
            session = reduceSession(session, SessionEvent.Review),
            attention = AttentionState.UNKNOWN,
        )
    }

    fun continueFragment() {
        val current = state.value
        val session = current.session ?: return
        if (session.phase != SessionPhase.DECISION) return
        val updated = reduceSession(session, SessionEvent.Continue(System.currentTimeMillis()))
        if (updated.phase == SessionPhase.SUMMARY) {
            finishSession(current, updated)
        } else {
            beginGracePeriod()
            state.value = current.copy(session = updated, attention = AttentionState.UNKNOWN)
        }
    }

    private fun finishSession(current: DictaUiState, session: SessionState) {
        val elapsed = ((session.completedAt ?: System.currentTimeMillis()) -
            (session.startedAt ?: System.currentTimeMillis())).coerceAtLeast(1_000L)
        val score = calculateScore(session.exercise.sourceText, elapsed, session.totalReviews)
        val previousBest = current.leaderboard.firstOrNull()?.score ?: 0
        val createdAt = System.currentTimeMillis()
        val entry = LeaderboardEntry(
            id = "$createdAt-$score-${current.leaderboard.size}",
            score = score,
            createdAt = createdAt,
        )
        val leaderboard = runCatching { store.saveScore(entry) }
            .getOrElse { current.leaderboard }
        state.value = current.copy(
            screen = AppScreen.SUMMARY,
            session = session,
            score = score,
            currentScoreId = entry.id,
            isNewBestScore = score > previousBest,
            leaderboard = leaderboard,
            attention = AttentionState.UNKNOWN,
            faceDetected = false,
        )
    }

    fun prepareNextDictation() {
        val level = progress.level
        val index = normalizedIndex(level, progress.cursors[level] ?: 0)
        val cursors = progress.cursors.toMutableMap().apply {
            this[level] = (index + 1) % level.texts.size
        }
        progress = progress.copy(
            index = index,
            cursors = cursors,
            lettersPerFragment = level.recommendedLetters,
        )
        persistProgress()
        state.value = stateFromProgress(progress)
    }

    fun cameraUnavailable(message: String) {
        val current = state.value
        if (current.screen == AppScreen.SESSION) {
            val session = current.session ?: return
            state.value = current.copy(
                session = session.copy(detectionMode = DetectionMode.MANUAL),
                attention = AttentionState.UNKNOWN,
                faceDetected = false,
                cameraMessage = message,
            )
        } else {
            state.value = stateFromProgress(progress, cameraMessage = message)
        }
    }

    fun fail(message: String) {
        state.value = state.value.copy(screen = AppScreen.ERROR, error = message)
    }

    fun reset() {
        state.value = stateFromProgress(progress)
    }

    private fun enforceCameraWatchdog() {
        val current = state.value
        val session = current.session ?: return
        val now = SystemClock.uptimeMillis()
        if (
            current.screen == AppScreen.SESSION &&
            session.phase == SessionPhase.MEMORIZING &&
            session.detectionMode == DetectionMode.CAMERA &&
            now >= autoHideBlockedUntil &&
            now - lastCameraReadingAt > 1_200L
        ) {
            state.value = current.copy(session = reduceSession(session, SessionEvent.LookedAway))
        }
    }

    private fun beginGracePeriod() {
        val now = SystemClock.uptimeMillis()
        autoHideBlockedUntil = now + 2_000L
        lastCameraReadingAt = now
    }

    private fun createSelectedExercise() = createExercise(
        level = progress.level,
        maxLetters = progress.lettersPerFragment,
        index = normalizedIndex(progress.level, progress.index),
    )

    private fun persistProgress() {
        runCatching { store.saveProgress(progress) }
    }

    private fun stateFromProgress(progress: StoredProgress, cameraMessage: String? = null): DictaUiState {
        val level = progress.level
        val index = normalizedIndex(level, progress.index)
        val text = level.texts[index]
        return DictaUiState(
            level = level,
            challengeIndex = index,
            challengeTotal = level.texts.size,
            maxLetters = progress.lettersPerFragment.coerceIn(1, 100),
            wordCount = text.trim().split(Regex("\\s+")).filter(String::isNotBlank).size,
            leaderboard = runCatching { store.readLeaderboard() }.getOrDefault(emptyList()),
            cameraMessage = cameraMessage,
            availableUpdate = latestUpdate,
            updateCheckState = latestUpdateCheckState,
        )
    }

    private fun normalizedIndex(level: SchoolLevel, value: Int): Int =
        ((value % level.texts.size) + level.texts.size) % level.texts.size
}

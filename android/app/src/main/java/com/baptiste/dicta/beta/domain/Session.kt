package com.baptiste.dicta.beta.domain

enum class DetectionMode { CAMERA, MANUAL }

enum class SessionPhase { SETUP, CALIBRATION, READY, MEMORIZING, DECISION, SUMMARY, ERROR }

data class SessionState(
    val exercise: Exercise,
    val id: String,
    val phase: SessionPhase = SessionPhase.SETUP,
    val detectionMode: DetectionMode = DetectionMode.CAMERA,
    val currentFragment: Int = 0,
    val reviewCounts: List<Int> = List(exercise.fragments.size) { 0 },
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val recoverable: Boolean = true,
) {
    val fragment: String get() = exercise.fragments[currentFragment]
    val isLastFragment: Boolean get() = currentFragment == exercise.fragments.lastIndex
    val totalReviews: Int get() = reviewCounts.sum()
}

sealed interface SessionEvent {
    object BeginCalibration : SessionEvent
    object ScreenCalibrated : SessionEvent
    object CalibrationCompleted : SessionEvent
    object UseManualMode : SessionEvent
    data class Start(val at: Long) : SessionEvent
    object LookedAway : SessionEvent
    object Review : SessionEvent
    data class Continue(val at: Long) : SessionEvent
    data class Fail(val message: String, val recoverable: Boolean = true) : SessionEvent
    object Reset : SessionEvent
}

fun createSession(exercise: Exercise, id: String, mode: DetectionMode = DetectionMode.CAMERA) =
    SessionState(exercise = exercise, id = id, detectionMode = mode)

fun reduceSession(state: SessionState, event: SessionEvent): SessionState {
    if (event is SessionEvent.Fail) return state.copy(
        phase = SessionPhase.ERROR,
        errorMessage = event.message,
        recoverable = event.recoverable,
    )
    if (event is SessionEvent.Reset) return createSession(state.exercise, state.id, state.detectionMode)

    return when (state.phase) {
        SessionPhase.SETUP -> when (event) {
            SessionEvent.BeginCalibration -> state.copy(phase = SessionPhase.CALIBRATION)
            SessionEvent.UseManualMode -> state.copy(phase = SessionPhase.READY, detectionMode = DetectionMode.MANUAL)
            else -> state
        }
        SessionPhase.CALIBRATION -> when (event) {
            SessionEvent.ScreenCalibrated, SessionEvent.CalibrationCompleted -> state.copy(phase = SessionPhase.READY)
            SessionEvent.UseManualMode -> state.copy(phase = SessionPhase.READY, detectionMode = DetectionMode.MANUAL)
            else -> state
        }
        SessionPhase.READY -> when (event) {
            is SessionEvent.Start -> state.copy(phase = SessionPhase.MEMORIZING, startedAt = event.at)
            else -> state
        }
        SessionPhase.MEMORIZING -> if (event is SessionEvent.LookedAway) state.copy(phase = SessionPhase.DECISION) else state
        SessionPhase.DECISION -> when (event) {
            SessionEvent.Review -> state.copy(
                phase = SessionPhase.MEMORIZING,
                reviewCounts = state.reviewCounts.mapIndexed { index, count -> if (index == state.currentFragment) count + 1 else count },
            )
            is SessionEvent.Continue -> if (state.isLastFragment) {
                state.copy(phase = SessionPhase.SUMMARY, completedAt = event.at)
            } else {
                state.copy(phase = SessionPhase.MEMORIZING, currentFragment = state.currentFragment + 1)
            }
            else -> state
        }
        SessionPhase.ERROR -> if (event is SessionEvent.UseManualMode && state.recoverable) {
            state.copy(phase = SessionPhase.READY, detectionMode = DetectionMode.MANUAL, errorMessage = null)
        } else state
        SessionPhase.SUMMARY -> state
    }
}

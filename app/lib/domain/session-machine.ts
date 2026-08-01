import { createReviewCounts, incrementReviewCount } from "./review-counts";
import type { CalibrationSample, DetectionMode, Exercise, SessionResult } from "./types";

interface SessionContext {
  exercise: Exercise;
  result: SessionResult;
  calibration?: CalibrationSample;
}

export type SessionState =
  | ({ status: "setup" } & SessionContext)
  | ({ status: "calibration"; step: "screen" | "notebook" } & SessionContext)
  | ({ status: "ready" } & SessionContext)
  | ({ status: "memorizing" } & SessionContext)
  | ({ status: "writing" } & SessionContext)
  | ({ status: "decision" } & SessionContext)
  | ({ status: "summary" } & SessionContext)
  | ({ status: "error"; message: string; recoverable: boolean } & SessionContext);

export type SessionEvent =
  | { type: "BEGIN_CALIBRATION" }
  | { type: "SCREEN_CALIBRATED" }
  | { type: "CALIBRATION_COMPLETED"; calibration: CalibrationSample }
  | { type: "USE_MANUAL_MODE" }
  | { type: "START"; startedAt: string }
  | { type: "LOOKED_AWAY" }
  | { type: "LOOKED_BACK" }
  | { type: "REVIEW" }
  | { type: "CONTINUE"; completedAt?: string }
  | { type: "FAIL"; message: string; recoverable?: boolean }
  | { type: "RESET" };

export function createSession(
  exercise: Exercise,
  sessionId: string,
  detectionMode: DetectionMode = "camera",
): SessionState {
  if (exercise.fragments.length === 0) {
    throw new Error("An exercise must contain at least one fragment");
  }

  return {
    status: "setup",
    exercise,
    result: {
      id: sessionId,
      exerciseId: exercise.id,
      startedAt: "",
      currentFragment: 0,
      reviewCounts: createReviewCounts(exercise.fragments.length),
      detectionMode,
    },
  };
}

export function currentFragment(state: SessionState): string | undefined {
  return state.exercise.fragments[state.result.currentFragment];
}

function withStatus<T extends SessionState["status"]>(
  state: SessionState,
  status: T,
): Extract<SessionState, { status: T }> {
  return { ...state, status } as Extract<SessionState, { status: T }>;
}

export function transitionSession(state: SessionState, event: SessionEvent): SessionState {
  if (event.type === "FAIL") {
    return {
      ...state,
      status: "error",
      message: event.message,
      recoverable: event.recoverable ?? true,
    };
  }

  if (event.type === "RESET") {
    return createSession(state.exercise, state.result.id, state.result.detectionMode);
  }

  switch (state.status) {
    case "setup":
      if (event.type === "BEGIN_CALIBRATION" && state.result.detectionMode === "camera") {
        return { ...state, status: "calibration", step: "screen" };
      }
      if (event.type === "USE_MANUAL_MODE") {
        return {
          ...state,
          status: "ready",
          result: { ...state.result, detectionMode: "manual" },
        };
      }
      break;

    case "calibration":
      if (event.type === "SCREEN_CALIBRATED" && state.step === "screen") {
        return { ...state, step: "notebook" };
      }
      if (event.type === "CALIBRATION_COMPLETED" && state.step === "notebook") {
        return { ...state, status: "ready", calibration: event.calibration };
      }
      if (event.type === "USE_MANUAL_MODE") {
        return {
          ...state,
          status: "ready",
          result: { ...state.result, detectionMode: "manual" },
        };
      }
      break;

    case "ready":
      if (event.type === "START") {
        return {
          ...state,
          status: "memorizing",
          result: { ...state.result, startedAt: event.startedAt },
        };
      }
      break;

    case "memorizing":
      if (event.type === "LOOKED_AWAY") return withStatus(state, "writing");
      break;

    case "writing":
      if (event.type === "LOOKED_BACK") return withStatus(state, "decision");
      break;

    case "decision":
      if (event.type === "REVIEW") {
        return {
          ...state,
          status: "memorizing",
          result: {
            ...state.result,
            reviewCounts: incrementReviewCount(
              state.result.reviewCounts,
              state.result.currentFragment,
            ),
          },
        };
      }
      if (event.type === "CONTINUE") {
        const isLast = state.result.currentFragment === state.exercise.fragments.length - 1;
        if (isLast) {
          return {
            ...state,
            status: "summary",
            result: { ...state.result, completedAt: event.completedAt },
          };
        }
        return {
          ...state,
          status: "memorizing",
          result: {
            ...state.result,
            currentFragment: state.result.currentFragment + 1,
          },
        };
      }
      break;

    case "error":
      if (event.type === "USE_MANUAL_MODE" && state.recoverable) {
        return {
          ...state,
          status: "ready",
          result: { ...state.result, detectionMode: "manual" },
        };
      }
      break;

    case "summary":
      break;
  }

  return state;
}


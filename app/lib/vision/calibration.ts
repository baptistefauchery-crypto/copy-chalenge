import type {
  AttentionCalibration,
  AttentionFeatures,
  AttentionState,
  CalibrationSample,
} from "./types";

export const FEATURE_KEYS = [
  "headPitch",
  "headYaw",
  "leftIrisY",
  "rightIrisY",
  "leftEyeOpen",
  "rightEyeOpen",
] as const satisfies readonly (keyof AttentionFeatures)[];

// A head turn must count as leaving the screen too. Eye opening varies a lot
// during normal blinks, so it is deliberately only a weak signal.
const FEATURE_WEIGHTS: Record<(typeof FEATURE_KEYS)[number], number> = {
  headPitch: 1.5,
  headYaw: 1.2,
  leftIrisY: 1.35,
  rightIrisY: 1.35,
  leftEyeOpen: 0.15,
  rightEyeOpen: 0.15,
};
const FEATURE_WEIGHT_TOTAL = FEATURE_KEYS.reduce((sum, key) => sum + FEATURE_WEIGHTS[key], 0);

export function summarizeSamples(samples: readonly AttentionFeatures[]): CalibrationSample {
  if (samples.length < 5) throw new Error("Calibration requires at least 5 valid face samples.");
  const mean = Object.fromEntries(
    FEATURE_KEYS.map((key) => [key, samples.reduce((sum, value) => sum + value[key], 0) / samples.length]),
  ) as AttentionFeatures;
  const deviation = Object.fromEntries(
    FEATURE_KEYS.map((key) => [
      key,
      Math.sqrt(samples.reduce((sum, value) => sum + (value[key] - mean[key]) ** 2, 0) / samples.length),
    ]),
  ) as AttentionFeatures;
  return { mean, deviation, sampleCount: samples.length };
}

function distanceTo(sample: AttentionFeatures, target: CalibrationSample, other: CalibrationSample) {
  const squared = FEATURE_KEYS.reduce((sum, key) => {
    // Pool both calibration variances and keep a noise floor to avoid unstable weights.
    const scale = Math.max((target.deviation[key] + other.deviation[key]) / 2, 0.025);
    return sum + FEATURE_WEIGHTS[key] * ((sample[key] - target.mean[key]) / scale) ** 2;
  }, 0);
  return Math.sqrt(squared / FEATURE_WEIGHT_TOTAL);
}

function distanceFromScreen(sample: AttentionFeatures, screen: CalibrationSample) {
  const squared = FEATURE_KEYS.reduce((sum, key) => {
    // A short natural calibration contains little movement. The floor keeps a
    // single unusually steady feature from dominating the whole decision.
    const scale = Math.max(screen.deviation[key], key.includes("Iris") ? 0.035 : 0.025);
    return sum + FEATURE_WEIGHTS[key] * ((sample[key] - screen.mean[key]) / scale) ** 2;
  }, 0);
  return Math.sqrt(squared / FEATURE_WEIGHT_TOTAL);
}

/**
 * Calibrates the one state the camera can observe unambiguously: looking at
 * this screen. Everything sufficiently unlike that reference is "notebook".
 */
export function createScreenCalibration(screen: CalibrationSample): AttentionCalibration {
  return { screen, quality: 1 };
}

export function createCalibration(
  screen: CalibrationSample,
  notebook: CalibrationSample,
): AttentionCalibration {
  const quality = distanceTo(screen.mean, notebook, screen);
  return { screen, notebook, quality };
}

export function classifyFeatures(
  sample: AttentionFeatures,
  calibration: AttentionCalibration,
): { state: Exclude<AttentionState, "unknown">; confidence: number } {
  if (!calibration.notebook) {
    const distance = distanceFromScreen(sample, calibration.screen);
    const screenRadius = 2.6;
    const margin = Math.abs(distance - screenRadius) / screenRadius;
    return {
      state: distance <= screenRadius ? "screen" : "notebook",
      // Hysteresis handles temporal noise. Keeping a small confidence floor
      // avoids preserving a stale "screen" state forever near the boundary.
      confidence: Math.min(1, 0.25 + margin * 0.75),
    };
  }
  const screenDistance = distanceTo(sample, calibration.screen, calibration.notebook);
  const notebookDistance = distanceTo(sample, calibration.notebook, calibration.screen);
  const total = Math.max(screenDistance + notebookDistance, 1e-5);
  const margin = Math.abs(screenDistance - notebookDistance) / total;
  // A close calibration should lower confidence, not block the whole session.
  // Temporal hysteresis still prevents a single ambiguous frame from switching state.
  const qualityFactor = Math.min(1, Math.max(0.45, calibration.quality / 2));
  return {
    state: screenDistance <= notebookDistance ? "screen" : "notebook",
    confidence: Math.min(1, margin * qualityFactor),
  };
}

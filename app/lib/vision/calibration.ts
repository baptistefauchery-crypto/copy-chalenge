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

// Looking down changes head pitch and iris position more reliably than blink or
// eye-opening measurements, so the classifier gives those signals more weight.
const FEATURE_WEIGHTS: Record<(typeof FEATURE_KEYS)[number], number> = {
  headPitch: 1.5,
  headYaw: 0.6,
  leftIrisY: 1.35,
  rightIrisY: 1.35,
  leftEyeOpen: 0.35,
  rightEyeOpen: 0.35,
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
  const screenDistance = distanceTo(sample, calibration.screen, calibration.notebook);
  const notebookDistance = distanceTo(sample, calibration.notebook, calibration.screen);
  const total = Math.max(screenDistance + notebookDistance, 1e-5);
  const margin = Math.abs(screenDistance - notebookDistance) / total;
  return {
    state: screenDistance <= notebookDistance ? "screen" : "notebook",
    confidence: Math.min(1, margin * Math.min(1, calibration.quality / 2)),
  };
}

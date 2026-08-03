import type {
  AttentionCalibration,
  AttentionFeatures,
  AttentionState,
  CalibrationSample,
} from "./types";

export const FEATURE_KEYS = [
  "headPitch",
  "headYaw",
  "leftIrisX",
  "leftIrisY",
  "rightIrisX",
  "rightIrisY",
  "leftEyeOpen",
  "rightEyeOpen",
] as const satisfies readonly (keyof AttentionFeatures)[];

// A head turn must count as leaving the screen too. Eye opening varies a lot
// during normal blinks, so it is deliberately only a weak signal.
const FEATURE_WEIGHTS: Record<(typeof FEATURE_KEYS)[number], number> = {
  headPitch: 1.5,
  headYaw: 1.2,
  leftIrisX: 1.35,
  leftIrisY: 1.35,
  rightIrisX: 1.35,
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

const tolerance = (deviation: number, floor: number) => Math.max(floor, deviation * 3);

/**
 * Compare iris displacement and head orientation independently. A small
 * movement is deliberately ambiguous: it must not be enough to hide the
 * fragment. Only a clearly different pose is classified as notebook.
 */
function classifyScreenOnly(sample: AttentionFeatures, screen: CalibrationSample) {
  const headPitchDelta = Math.abs(sample.headPitch - screen.mean.headPitch);
  const headYawDelta = Math.abs(sample.headYaw - screen.mean.headYaw);
  const headPitchDistance = headPitchDelta / tolerance(screen.deviation.headPitch, 0.045);
  const headYawDistance = headYawDelta / tolerance(screen.deviation.headYaw, 0.06);

  const eyeDistance = (side: "left" | "right") => {
    const xKey = `${side}IrisX` as const;
    const yKey = `${side}IrisY` as const;
    const x = (sample[xKey] - screen.mean[xKey]) / tolerance(screen.deviation[xKey], 0.09);
    const y = (sample[yKey] - screen.mean[yKey]) / tolerance(screen.deviation[yKey], 0.10);
    return Math.hypot(x, y);
  };
  const leftEyeDistance = eyeDistance("left");
  const rightEyeDistance = eyeDistance("right");
  const eyesAreOpen = sample.leftEyeOpen > 0.08 && sample.rightEyeOpen > 0.08;
  const clearlyScreen = eyesAreOpen &&
    headPitchDistance <= 1.2 &&
    headYawDistance <= 1.2 &&
    leftEyeDistance <= 1.2 &&
    rightEyeDistance <= 1.2;
  const clearlyNotebook = eyesAreOpen && (
    headPitchDistance >= 1.7 ||
    headYawDistance >= 1.7 ||
    (leftEyeDistance >= 1.7 && rightEyeDistance >= 1.7)
  );

  if (clearlyScreen) return { state: "screen" as const, confidence: 1 };
  if (clearlyNotebook) return { state: "notebook" as const, confidence: 1 };
  return { state: "unknown" as const, confidence: 0 };
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
): { state: AttentionState; confidence: number } {
  if (!calibration.notebook) {
    return classifyScreenOnly(sample, calibration.screen);
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

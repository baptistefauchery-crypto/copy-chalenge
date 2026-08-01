/** A normalized MediaPipe-compatible landmark. */
export type FaceLandmark = {
  x: number;
  y: number;
  z?: number;
  visibility?: number;
  presence?: number;
};

export type AttentionState = "screen" | "notebook" | "unknown";

/** Compact, scale-independent description of a face pose and its eyes. */
export type AttentionFeatures = {
  headPitch: number;
  headYaw: number;
  leftIrisY: number;
  rightIrisY: number;
  leftEyeOpen: number;
  rightEyeOpen: number;
};

export type CalibrationSample = {
  mean: AttentionFeatures;
  deviation: AttentionFeatures;
  sampleCount: number;
};

export type AttentionCalibration = {
  screen: CalibrationSample;
  /** Optional legacy second pose. A screen-only calibration is preferred. */
  notebook?: CalibrationSample;
  /** Separation of the two centroids in standardized feature space. */
  quality: number;
};

export type AttentionReading = {
  state: AttentionState;
  confidence: number;
  features: AttentionFeatures | null;
  faceDetected: boolean;
  timestamp: number;
};

export type DetectorListener = (reading: AttentionReading) => void;

export interface AttentionDetector {
  start(video?: HTMLVideoElement): Promise<void>;
  stop(): void;
  beginCalibration(target: "screen" | "notebook"): void;
  finishCalibration(target: "screen" | "notebook"): CalibrationSample;
  getCalibration(): AttentionCalibration | null;
  getReading(): AttentionReading;
  subscribe(listener: DetectorListener): () => void;
}

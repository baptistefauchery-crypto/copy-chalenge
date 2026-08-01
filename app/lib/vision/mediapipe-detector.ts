import { classifyFeatures, createCalibration, createScreenCalibration, summarizeSamples } from "./calibration";
import { extractAttentionFeatures } from "./features";
import { AttentionStabilizer, type StabilizerOptions } from "./smoothing";
import type {
  AttentionCalibration,
  AttentionDetector,
  AttentionFeatures,
  AttentionReading,
  CalibrationSample,
  DetectorListener,
  FaceLandmark,
} from "./types";

export type MediaPipeDetectorOptions = StabilizerOptions & {
  modelAssetPath?: string;
  wasmPath?: string;
  analysisFps?: number;
};

/** Browser-only MediaPipe implementation. Video frames never leave the device. */
export class MediaPipeAttentionDetector implements AttentionDetector {
  private video: HTMLVideoElement | null = null;
  private stream: MediaStream | null = null;
  private landmarker: { detectForVideo(video: HTMLVideoElement, timestamp: number): { faceLandmarks?: FaceLandmark[][] }; close(): void } | null = null;
  private frameId: number | null = null;
  private lastAnalysis = 0;
  private readonly listeners = new Set<DetectorListener>();
  private readonly samples: Record<"screen" | "notebook", AttentionFeatures[]> = { screen: [], notebook: [] };
  private calibrationParts: Partial<Record<"screen" | "notebook", CalibrationSample>> = {};
  private calibration: AttentionCalibration | null = null;
  private collecting: "screen" | "notebook" | null = null;
  private readonly stabilizer: AttentionStabilizer;
  private reading: AttentionReading = { state: "unknown", confidence: 0, features: null, faceDetected: false, timestamp: 0 };

  constructor(private readonly options: MediaPipeDetectorOptions = {}) {
    this.stabilizer = new AttentionStabilizer(options);
  }

  async start(video?: HTMLVideoElement): Promise<void> {
    if (typeof window === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      throw new Error("The attention detector requires a browser with camera support.");
    }
    this.stop();
    this.video = video ?? document.createElement("video");
    this.video.muted = true;
    this.video.playsInline = true;

    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } },
    });
    this.video.srcObject = this.stream;
    await this.video.play();

    const { FaceLandmarker, FilesetResolver } = await import("@mediapipe/tasks-vision");
    const vision = await FilesetResolver.forVisionTasks(
      this.options.wasmPath ?? "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm",
    );
    this.landmarker = await FaceLandmarker.createFromOptions(vision, {
      baseOptions: {
        modelAssetPath:
          this.options.modelAssetPath ??
          "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
        delegate: "GPU",
      },
      runningMode: "VIDEO",
      numFaces: 1,
      minFaceDetectionConfidence: 0.5,
      minFacePresenceConfidence: 0.5,
      minTrackingConfidence: 0.5,
      outputFaceBlendshapes: false,
      outputFacialTransformationMatrixes: false,
    });
    this.frameId = requestAnimationFrame(this.tick);
  }

  stop(): void {
    if (this.frameId !== null) cancelAnimationFrame(this.frameId);
    this.frameId = null;
    this.landmarker?.close();
    this.landmarker = null;
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    if (this.video) this.video.srcObject = null;
    this.video = null;
    this.collecting = null;
    this.stabilizer.reset();
  }

  beginCalibration(target: "screen" | "notebook"): void {
    this.samples[target] = [];
    if (target === "screen") {
      this.calibration = null;
      this.calibrationParts = {};
      this.stabilizer.reset();
    }
    this.collecting = target;
  }

  finishCalibration(target: "screen" | "notebook"): CalibrationSample {
    if (this.collecting === target) this.collecting = null;
    const sample = summarizeSamples(this.samples[target]);
    this.calibrationParts[target] = sample;
    if (target === "screen") {
      this.calibration = createScreenCalibration(sample);
    } else if (this.calibrationParts.screen && this.calibrationParts.notebook) {
      this.calibration = createCalibration(this.calibrationParts.screen, this.calibrationParts.notebook);
    }
    return sample;
  }

  getCalibration() { return this.calibration; }
  getReading() { return this.reading; }

  subscribe(listener: DetectorListener): () => void {
    this.listeners.add(listener);
    listener(this.reading);
    return () => this.listeners.delete(listener);
  }

  private readonly tick = (timestamp: number) => {
    this.frameId = requestAnimationFrame(this.tick);
    const interval = 1000 / (this.options.analysisFps ?? 10);
    if (!this.landmarker || !this.video || timestamp - this.lastAnalysis < interval) return;
    if (this.video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
    this.lastAnalysis = timestamp;
    const result = this.landmarker.detectForVideo(this.video, timestamp);
    const features = result.faceLandmarks?.[0]
      ? extractAttentionFeatures(result.faceLandmarks[0])
      : null;

    if (!features) {
      this.publish({ state: this.stabilizer.loseFace(), confidence: 1, features: null, faceDetected: false, timestamp });
      return;
    }
    if (this.collecting) this.samples[this.collecting].push(features);
    if (!this.calibration) {
      this.publish({ state: "unknown", confidence: 0, features, faceDetected: true, timestamp });
      return;
    }
    const raw = classifyFeatures(features, this.calibration);
    const state = this.stabilizer.update(raw.state, raw.confidence, timestamp);
    this.publish({ state, confidence: raw.confidence, features, faceDetected: true, timestamp });
  };

  private publish(reading: AttentionReading) {
    this.reading = reading;
    this.listeners.forEach((listener) => listener(reading));
  }
}

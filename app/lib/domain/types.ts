export type DetectionMode = "camera" | "manual";

export interface Exercise {
  id: string;
  title: string;
  originalText: string;
  fragments: string[];
  createdAt: string;
}

export interface SessionResult {
  id: string;
  exerciseId: string;
  startedAt: string;
  completedAt?: string;
  currentFragment: number;
  reviewCounts: number[];
  detectionMode: DetectionMode;
}

export interface CalibrationSample {
  screenFeatures: number[];
  notebookFeatures: number[];
  threshold: number;
  quality: number;
}


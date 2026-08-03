export type DetectionMode = "camera" | "manual";

/** Controls how the learning text is divided into challenge fragments. */
export type FragmentMode = "letters" | "verses";

/**
 * Metadata kept separately from the displayed text so the UI can render a
 * verse-end cue without adding a character that would pollute OCR comparison.
 */
export interface TextFragment {
  text: string;
  endsVerse: boolean;
}

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

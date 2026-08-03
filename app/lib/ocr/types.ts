export type OcrComparisonStatus = "match" | "probable" | "uncertain";

export type OcrCharacterDiffKind = "equal" | "insert" | "delete" | "replace";

export type OcrWordDiffKind = "equal" | "insert" | "delete" | "replace";

export type OcrToken = {
  text: string;
  confidence?: number;
};

export type OcrInput = string | {
  text: string;
  confidence?: number;
  tokens?: OcrToken[];
};

export type OcrCharacterDiff = {
  kind: OcrCharacterDiffKind;
  reference: string;
  recognized: string;
  status: OcrComparisonStatus;
};

export type OcrWordDiff = {
  kind: OcrWordDiffKind;
  reference: string;
  recognized: string;
  referenceIndex: number | null;
  recognizedIndex: number | null;
  status: OcrComparisonStatus;
  characterDiffs: OcrCharacterDiff[];
};

export type OcrComparisonOptions = {
  /** Minimum overall confidence for a mismatch to be considered probable. */
  probableConfidence?: number;
  /** Confidence below which even an exact OCR match remains uncertain. */
  uncertainConfidence?: number;
};

export type OcrComparisonResult = {
  referenceText: string;
  recognizedText: string;
  normalizedReferenceText: string;
  normalizedRecognizedText: string;
  confidence: number;
  status: OcrComparisonStatus;
  matches: boolean;
  wordDiffs: OcrWordDiff[];
  characterDiffs: OcrCharacterDiff[];
};

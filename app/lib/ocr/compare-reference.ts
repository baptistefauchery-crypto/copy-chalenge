import type {
  OcrCharacterDiff,
  OcrComparisonOptions,
  OcrComparisonResult,
  OcrComparisonStatus,
  OcrInput,
  OcrToken,
  OcrWordDiff,
} from "./types";

const DEFAULT_PROBABLE_CONFIDENCE = 0.8;
const DEFAULT_UNCERTAIN_CONFIDENCE = 0.55;

type SequenceEdit = {
  kind: "equal" | "insert" | "delete" | "replace";
  reference: string;
  recognized: string;
};

type Word = {
  text: string;
  confidence?: number;
};

/**
 * Applies only lossless-safe comparison normalizations. The original OCR text
 * is always returned unchanged by compareOcrToReference.
 */
export function normalizeForComparison(value: string): string {
  return value
    .normalize("NFC")
    .replace(/\r\n?/g, "\n")
    .replace(/[\u2018\u2019\u02BC]/g, "'")
    .replace(/[\t\n\f\r ]+/g, " ")
    .trim();
}

export function compareOcrToReference(
  referenceText: string,
  ocr: OcrInput,
  options: OcrComparisonOptions = {},
): OcrComparisonResult {
  const input = typeof ocr === "string" ? { text: ocr } : ocr;
  const normalizedReferenceText = normalizeForComparison(referenceText);
  const normalizedRecognizedText = normalizeForComparison(input.text);
  const confidence = resolveConfidence(input);
  const probableConfidence = options.probableConfidence ?? DEFAULT_PROBABLE_CONFIDENCE;
  const uncertainConfidence = options.uncertainConfidence ?? DEFAULT_UNCERTAIN_CONFIDENCE;
  const exact = normalizedReferenceText === normalizedRecognizedText;
  const status = getStatus(exact, confidence, probableConfidence, uncertainConfidence);
  const referenceWords = extractWords(normalizedReferenceText);
  const recognizedWords = extractWords(normalizedRecognizedText, input.tokens);
  const wordEdits = diffSequences(referenceWords, recognizedWords, (word) => word.text);
  const wordDiffs = toWordDiffs(wordEdits, confidence, probableConfidence);

  return {
    referenceText,
    recognizedText: input.text,
    normalizedReferenceText,
    normalizedRecognizedText,
    confidence,
    status,
    matches: exact,
    wordDiffs,
    characterDiffs: toCharacterDiffs(
      diffSequences([...normalizedReferenceText], [...normalizedRecognizedText], (character) => character),
      confidence,
      probableConfidence,
    ),
  };
}

function resolveConfidence(input: Exclude<OcrInput, string>): number {
  if (typeof input.confidence === "number") return clampConfidence(input.confidence);
  const tokenConfidences = (input.tokens ?? [])
    .map((token) => token.confidence)
    .filter((confidence): confidence is number => typeof confidence === "number");
  if (tokenConfidences.length === 0) return 1;
  return clampConfidence(tokenConfidences.reduce((sum, value) => sum + value, 0) / tokenConfidences.length);
}

function clampConfidence(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function getStatus(
  exact: boolean,
  confidence: number,
  probableConfidence: number,
  uncertainConfidence: number,
): OcrComparisonStatus {
  if (exact) return confidence < uncertainConfidence ? "uncertain" : "match";
  return confidence >= probableConfidence ? "probable" : "uncertain";
}

function extractWords(text: string, tokens?: OcrToken[]): Word[] {
  if (tokens && tokens.length > 0) {
    return tokens.map((token) => ({ text: normalizeForComparison(token.text), confidence: token.confidence }));
  }
  return [...text.matchAll(/[\p{L}\p{M}\p{N}]+(?:'[\p{L}\p{M}\p{N}]+)*/gu)].map((match) => ({ text: match[0] }));
}

function diffSequences<T>(
  reference: T[],
  recognized: T[],
  key: (value: T) => string,
): SequenceEdit[] {
  const scores = Array.from({ length: reference.length + 1 }, () => Array<number>(recognized.length + 1).fill(0));
  for (let row = 1; row <= reference.length; row += 1) {
    for (let column = 1; column <= recognized.length; column += 1) {
      scores[row][column] = key(reference[row - 1]) === key(recognized[column - 1])
        ? scores[row - 1][column - 1] + 1
        : Math.max(scores[row - 1][column], scores[row][column - 1]);
    }
  }

  const edits: SequenceEdit[] = [];
  let row = reference.length;
  let column = recognized.length;
  while (row > 0 || column > 0) {
    if (row > 0 && column > 0 && key(reference[row - 1]) === key(recognized[column - 1])) {
      edits.push({ kind: "equal", reference: key(reference[row - 1]), recognized: key(recognized[column - 1]) });
      row -= 1;
      column -= 1;
    } else if (row > 0 && column > 0 && scores[row - 1][column - 1] >= Math.max(scores[row - 1][column], scores[row][column - 1])) {
      edits.push({ kind: "replace", reference: key(reference[row - 1]), recognized: key(recognized[column - 1]) });
      row -= 1;
      column -= 1;
    } else if (row > 0 && (column === 0 || scores[row - 1][column] >= scores[row][column - 1])) {
      edits.push({ kind: "delete", reference: key(reference[row - 1]), recognized: "" });
      row -= 1;
    } else {
      edits.push({ kind: "insert", reference: "", recognized: key(recognized[column - 1]) });
      column -= 1;
    }
  }
  return edits.reverse();
}

function statusForMismatch(confidence: number, probableConfidence: number): OcrComparisonStatus {
  return confidence >= probableConfidence ? "probable" : "uncertain";
}

function toWordDiffs(edits: SequenceEdit[], confidence: number, probableConfidence: number): OcrWordDiff[] {
  let referenceIndex = 0;
  let recognizedIndex = 0;
  return edits.map((edit) => {
    const result: OcrWordDiff = {
      kind: edit.kind,
      reference: edit.reference,
      recognized: edit.recognized,
      referenceIndex: edit.kind === "insert" ? null : referenceIndex,
      recognizedIndex: edit.kind === "delete" ? null : recognizedIndex,
      status: edit.kind === "equal" ? "match" : statusForMismatch(confidence, probableConfidence),
      characterDiffs: edit.kind === "equal" ? [] : toCharacterDiffs(
        diffSequences([...edit.reference], [...edit.recognized], (character) => character),
        confidence,
        probableConfidence,
      ),
    };
    if (edit.kind !== "insert") referenceIndex += 1;
    if (edit.kind !== "delete") recognizedIndex += 1;
    return result;
  });
}

function toCharacterDiffs(edits: SequenceEdit[], confidence: number, probableConfidence: number): OcrCharacterDiff[] {
  return edits.map((edit) => ({
    kind: edit.kind,
    reference: edit.reference,
    recognized: edit.recognized,
    status: edit.kind === "equal" ? "match" : statusForMismatch(confidence, probableConfidence),
  }));
}

import { detectTextRegions, type OcrTextRegion } from "./paddle";
import type { OcrToken } from "./types";

const TROCR_MODEL_ID = "Xenova/trocr-small-handwritten";
const TROCR_MAX_NEW_TOKENS = 96;

type TrOcrOutput = { generated_text: string }[];
type TrOcrPipeline = (image: Blob, options?: { max_new_tokens?: number }) => Promise<TrOcrOutput>;

let recognizerPromise: Promise<TrOcrPipeline> | null = null;

async function createRecognizer(): Promise<TrOcrPipeline> {
  const { pipeline } = await import("@huggingface/transformers");
  const recognizer = await pipeline("image-to-text", TROCR_MODEL_ID, {
    device: "wasm",
    dtype: "q8",
  });
  return recognizer as unknown as TrOcrPipeline;
}

function getRecognizer() {
  if (!recognizerPromise) {
    recognizerPromise = createRecognizer().catch((error) => {
      recognizerPromise = null;
      throw error;
    });
  }
  return recognizerPromise;
}

function getRegionBounds(region: OcrTextRegion, width: number, height: number) {
  const left = Math.max(0, Math.floor(Math.min(...region.poly.map((point) => point[0]))));
  const top = Math.max(0, Math.floor(Math.min(...region.poly.map((point) => point[1]))));
  const right = Math.min(width, Math.ceil(Math.max(...region.poly.map((point) => point[0]))));
  const bottom = Math.min(height, Math.ceil(Math.max(...region.poly.map((point) => point[1]))));
  const paddingX = Math.max(12, Math.round((right - left) * 0.05));
  const paddingY = Math.max(10, Math.round((bottom - top) * 0.2));
  const x = Math.max(0, left - paddingX);
  const y = Math.max(0, top - paddingY);
  const endX = Math.min(width, right + paddingX);
  const endY = Math.min(height, bottom + paddingY);

  return { x, y, width: Math.max(1, endX - x), height: Math.max(1, endY - y) };
}

function cropRegion(canvas: HTMLCanvasElement, region: OcrTextRegion): Promise<Blob> {
  const bounds = getRegionBounds(region, canvas.width, canvas.height);
  const crop = document.createElement("canvas");
  crop.width = bounds.width;
  crop.height = bounds.height;
  const context = crop.getContext("2d");
  if (!context) throw new Error("Impossible de préparer une ligne pour l’OCR.");

  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, crop.width, crop.height);
  context.drawImage(
    canvas,
    bounds.x,
    bounds.y,
    bounds.width,
    bounds.height,
    0,
    0,
    bounds.width,
    bounds.height,
  );

  return new Promise((resolve, reject) => {
    crop.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("Impossible d’exporter une ligne pour l’OCR."));
    }, "image/png");
  });
}

function getGeneratedText(output: TrOcrOutput): string {
  return output
    .map((item) => item.generated_text.trim())
    .filter(Boolean)
    .join(" ");
}

export interface HandwritingOcrResult {
  text: string;
  tokens: OcrToken[];
  confidence: number;
  detectedLines: number;
  processingMs: number;
}

/**
 * Detects writing lines with the existing mobile detector, then recognizes
 * each crop with TrOCR Small Handwritten. TrOCR is intentionally fed one line
 * at a time because that is the input shape it was trained for.
 */
export async function recognizeHandwrittenText(
  image: Blob,
  sourceCanvas?: HTMLCanvasElement,
): Promise<HandwritingOcrResult> {
  const startedAt = performance.now();
  const { regions } = await detectTextRegions(image);
  const recognizer = await getRecognizer();
  const lineImages = sourceCanvas
    ? await Promise.all(regions.map((region) => cropRegion(sourceCanvas, region)))
    : [image];
  const tokens: OcrToken[] = [];

  for (let index = 0; index < lineImages.length; index += 1) {
    const output = await recognizer(lineImages[index], { max_new_tokens: TROCR_MAX_NEW_TOKENS });
    const text = getGeneratedText(output);
    if (text) {
      tokens.push({ text, confidence: regions[index]?.confidence });
    }
  }

  if (tokens.length === 0) {
    throw new Error("Aucun texte manuscrit n’a pu être reconnu sur la feuille.");
  }

  const confidenceValues = tokens
    .map((token) => token.confidence)
    .filter((confidence): confidence is number => typeof confidence === "number");
  const confidence = confidenceValues.length > 0
    ? confidenceValues.reduce((sum, value) => sum + value, 0) / confidenceValues.length
    : 0;

  return {
    text: tokens.map((token) => token.text).join(" "),
    tokens,
    confidence,
    detectedLines: regions.length,
    processingMs: Math.round(performance.now() - startedAt),
  };
}

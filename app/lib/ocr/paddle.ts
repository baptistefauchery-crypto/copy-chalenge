import type { OcrResult, PaddleOCRCreateOptions } from "@paddleocr/paddleocr-js";

import type { OcrToken } from "./types";

const OCR_MODEL_BASE_PATH = "/models/ocr";
const OCR_WASM_PATH = "/ocr/wasm/";

const PADDLE_OPTIONS: PaddleOCRCreateOptions = {
  lang: "fr",
  ocrVersion: "PP-OCRv6",
  textDetectionModelName: "PP-OCRv6_small_det",
  textDetectionModelAsset: {
    url: OCR_MODEL_BASE_PATH + "/PP-OCRv6_small_det_onnx_infer.tar",
  },
  textRecognitionModelName: "PP-OCRv6_small_rec",
  textRecognitionModelAsset: {
    url: OCR_MODEL_BASE_PATH + "/PP-OCRv6_small_rec_onnx_infer.tar",
  },
  worker: true,
  ortOptions: {
    backend: "wasm",
    wasmPaths: OCR_WASM_PATH,
    numThreads: 1,
    simd: true,
  },
};

interface PaddleOcrRunner {
  predict(input: Blob): Promise<OcrResult[]>;
  dispose(): Promise<void>;
}

export interface HandwritingOcrResult {
  text: string;
  tokens: OcrToken[];
  confidence: number;
  detectedLines: number;
  processingMs: number;
}

let ocrPromise: Promise<PaddleOcrRunner> | null = null;

async function createOcrRunner(): Promise<PaddleOcrRunner> {
  const { PaddleOCR } = await import("@paddleocr/paddleocr-js");
  const runner = await PaddleOCR.create(PADDLE_OPTIONS);
  return runner as PaddleOcrRunner;
}

function getOcrRunner() {
  if (!ocrPromise) {
    ocrPromise = createOcrRunner().catch((error) => {
      ocrPromise = null;
      throw error;
    });
  }
  return ocrPromise;
}

function getTopLeft(poly: OcrResult["items"][number]["poly"]) {
  return {
    top: Math.min(...poly.map((point) => point[1])),
    left: Math.min(...poly.map((point) => point[0])),
  };
}

function orderItems(result: OcrResult) {
  return result.items
    .filter((item) => item.text.trim())
    .map((item, index) => ({ item, index, position: getTopLeft(item.poly) }))
    .sort((a, b) => a.position.top - b.position.top || a.position.left - b.position.left || a.index - b.index)
    .map(({ item }) => item);
}

export async function recognizeHandwrittenText(image: Blob): Promise<HandwritingOcrResult> {
  const startedAt = performance.now();
  const runner = await getOcrRunner();
  const [result] = await runner.predict(image);
  const orderedItems = result ? orderItems(result) : [];

  if (orderedItems.length === 0) {
    throw new Error("Aucun texte lisible n’a été détecté sur la feuille.");
  }

  const tokens = orderedItems.map((item): OcrToken => ({
    text: item.text,
    confidence: item.score,
  }));
  const confidence = tokens.reduce((sum, token) => sum + (token.confidence ?? 0), 0) / tokens.length;

  return {
    text: orderedItems.map((item) => item.text).join(" "),
    tokens,
    confidence,
    detectedLines: orderedItems.length,
    processingMs: Math.round(performance.now() - startedAt),
  };
}

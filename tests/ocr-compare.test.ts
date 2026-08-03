import assert from "node:assert/strict";
import test from "node:test";

import { compareOcrToReference, normalizeForComparison } from "../app/lib/ocr/compare-reference.ts";

test("normalizes Unicode composition, safe apostrophe variants, and whitespace only", () => {
  assert.equal(normalizeForComparison("  L\u0065\u0301cole\r\n d’Anna  "), "Lécole d'Anna");
  assert.equal(normalizeForComparison("pomme,   poire"), "pomme, poire");
});

test("reports an exact comparison without changing the OCR text", () => {
  const result = compareOcrToReference("L'été arrive.", "L’été arrive.");

  assert.equal(result.matches, true);
  assert.equal(result.status, "match");
  assert.equal(result.recognizedText, "L’été arrive.");
  assert.equal(result.wordDiffs.filter((diff) => diff.status !== "match").length, 0);
});

test("reports word and character replacements as probable at high confidence", () => {
  const result = compareOcrToReference("Les enfants jouent", {
    text: "Les enfant jouent",
    confidence: 0.94,
  });

  assert.equal(result.matches, false);
  assert.equal(result.status, "probable");
  assert.deepEqual(result.wordDiffs.filter((diff) => diff.status !== "match").map((diff) => [diff.kind, diff.reference, diff.recognized]), [
    ["replace", "enfants", "enfant"],
  ]);
  assert.ok(result.wordDiffs[1].characterDiffs.some((diff) => diff.kind === "delete"));
  assert.ok(result.characterDiffs.some((diff) => diff.kind === "delete"));
});

test("marks a mismatch uncertain when OCR confidence is low", () => {
  const result = compareOcrToReference("Le lapin court", {
    text: "Le sapin court",
    confidence: 0.42,
  });

  assert.equal(result.status, "uncertain");
  assert.equal(result.wordDiffs[1].status, "uncertain");
  assert.equal(result.wordDiffs[1].reference, "lapin");
  assert.equal(result.wordDiffs[1].recognized, "sapin");
});

test("distinguishes missing and added words", () => {
  const added = compareOcrToReference("Le chat dort", {
    text: "Le chat très dort",
    confidence: 0.9,
  });
  const missing = compareOcrToReference("Le chat dort", {
    text: "Le dort",
    confidence: 0.9,
  });

  assert.ok(added.wordDiffs.some((diff) => diff.kind === "insert" && diff.recognized === "très"));
  assert.ok(missing.wordDiffs.some((diff) => diff.kind === "delete" && diff.reference === "chat"));
});

test("uses token confidence when no overall OCR confidence is supplied", () => {
  const result = compareOcrToReference("Bonjour monde", {
    text: "Bonjour mOndz",
    tokens: [
      { text: "Bonjour", confidence: 0.96 },
      { text: "mOndz", confidence: 0.44 },
    ],
  });

  assert.equal(result.confidence, 0.7);
  assert.equal(result.status, "uncertain");
});

test("splits line-level OCR tokens and ignores case differences", () => {
  const result = compareOcrToReference("Lina a un vélo", {
    text: "lina a un vélo",
    confidence: 0.91,
    tokens: [{ text: "lina a un vélo", confidence: 0.91 }],
  });

  assert.equal(result.matches, true);
  assert.equal(result.wordDiffs.filter((diff) => diff.kind !== "equal").length, 0);
});

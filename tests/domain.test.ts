import assert from "node:assert/strict";
import test from "node:test";
import {
  calculateScore,
  calculateScoreBreakdown,
  getScoreReward,
  splitTextIntoFragments,
  type ScoreCalculationOptions,
} from "../app/lib/domain/index.ts";

const standardSpeed: ScoreCalculationOptions = { speedReferenceLettersPerSecond: 2 };

test("splits text at word and sentence boundaries", () => {
  assert.deepEqual(
    splitTextIntoFragments("  Un   petit chat dort.  Il rêve dans la maison. ", { targetLetters: 12 }),
    ["Un petit chat dort.", "Il rêve dans la maison."],
  );
  assert.deepEqual(splitTextIntoFragments("Alpha beta. Gamma delta.", { targetLetters: 100 }), ["Alpha beta.", "Gamma delta."]);
  assert.deepEqual(splitTextIntoFragments(" \n\t ", { targetLetters: 8 }), []);
});

test("calculates a score from time, length, and reviews", () => {
  assert.equal(calculateScore("Le chat dort", 5_000, 0, standardSpeed), 100);
  assert.equal(calculateScore("Le chat dort", 10_000, 0, standardSpeed), 98);
  assert.equal(calculateScore("Le chat dort", 5_000, 1, standardSpeed), 82);
  assert.equal(calculateScore("", 30_000, 0, standardSpeed), 0);
  assert.equal(calculateScore("abcd", 4_000), 98);
});

test("caps a long and fast score while preserving its breakdown", () => {
  const result = calculateScoreBreakdown("a".repeat(250), 83_333, 0, standardSpeed);
  assert.equal(result.rawScore, 111);
  assert.equal(result.score, 100);
  assert.equal(result.lengthBonus, 5);
  assert.equal(result.speedAdjustment, 6);
});

test("reviews have a stronger effect than a slower pace", () => {
  const baseline = calculateScoreBreakdown("a".repeat(50), 33_333, 0, standardSpeed);
  const oneReview = calculateScoreBreakdown("a".repeat(50), 33_333, 1, standardSpeed);
  const slowest = calculateScoreBreakdown("a".repeat(50), Number.MAX_SAFE_INTEGER, 0, standardSpeed);
  assert.ok(Math.abs(baseline.rawScore - 100) < 0.001);
  assert.equal(oneReview.reviewMultiplier, 0.8);
  assert.ok(baseline.rawScore - oneReview.rawScore > baseline.rawScore - slowest.rawScore);
  assert.ok(Math.abs(slowest.speedAdjustment + 6) < 0.001);
});

test("malformed timing stays finite and bounded", () => {
  const result = calculateScoreBreakdown("abcdefghij", Number.NaN, -4, {
    speedReferenceLettersPerSecond: Number.NaN,
  });
  assert.ok(Number.isFinite(result.rawScore));
  assert.ok(result.score >= 0 && result.score <= 100);
  assert.equal(result.reviewMultiplier, 1);
});

test("rewards keep their stable thresholds", () => {
  assert.deepEqual(getScoreReward(20), { stars: 1, badge: "none" });
  assert.deepEqual(getScoreReward(70), { stars: 3, badge: "bronze" });
  assert.deepEqual(getScoreReward(80), { stars: 3, badge: "silver" });
  assert.deepEqual(getScoreReward(90), { stars: 3, badge: "gold" });
  assert.deepEqual(getScoreReward(100), { stars: 3, badge: "trophy" });
});

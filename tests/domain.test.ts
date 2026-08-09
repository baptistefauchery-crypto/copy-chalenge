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
  assert.equal(calculateScore("Le chat dort", 10_000, 0, standardSpeed), 80);
  assert.equal(calculateScore("Le chat dort", 10_000, 1, standardSpeed), 64);
  assert.equal(calculateScore("", 30_000, 0, standardSpeed), 0);
  assert.equal(calculateScore("abcd", 4_000), 80);
});

test("caps a fast score while preserving its uncapped breakdown", () => {
  const result = calculateScoreBreakdown("a".repeat(250), 100_000, 0, standardSpeed);
  assert.equal(result.rawScore, 200);
  assert.equal(result.score, 100);
  assert.equal(result.baseScore, 200);
  assert.equal(result.lengthBonus, 0);
  assert.equal(result.speedAdjustment, 0);
});

test("reviews apply a compounding twenty-percent penalty", () => {
  const baseline = calculateScoreBreakdown("a".repeat(50), 50_000, 0, standardSpeed);
  const oneReview = calculateScoreBreakdown("a".repeat(50), 50_000, 1, standardSpeed);
  const twoReviews = calculateScoreBreakdown("a".repeat(50), 50_000, 2, standardSpeed);
  assert.equal(baseline.rawScore, 80);
  assert.equal(oneReview.reviewMultiplier, 0.8);
  assert.equal(oneReview.rawScore, 64);
  assert.ok(Math.abs(twoReviews.rawScore - 51.2) < 0.001);
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

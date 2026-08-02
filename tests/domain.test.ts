import assert from "node:assert/strict";
import test from "node:test";

import {
  countLetters,
  calculateScore,
  createSession,
  getDictation,
  PRIMARY_LEVELS,
  getScoreReward,
  splitTextIntoFragments,
  sortLeaderboard,
  totalReviews,
  transitionSession,
  type Exercise,
} from "../app/lib/domain/index.ts";

const exercise: Exercise = {
  id: "exercise-1",
  title: "Test",
  originalText: "Le chat dort. Puis il se réveille doucement.",
  fragments: ["Le chat dort.", "Puis il se réveille doucement."],
  createdAt: "2026-08-01T09:00:00.000Z",
};

test("offers three deterministic dictations for every primary class", () => {
  assert.deepEqual(PRIMARY_LEVELS.map((level) => level.id), ["CP", "CE1", "CE2", "CM1", "CM2"]);
  assert.deepEqual(PRIMARY_LEVELS.map((level) => level.recommendedLetters), [6, 8, 10, 12, 14]);

  for (const level of PRIMARY_LEVELS) {
    assert.ok(level.dictations.length >= 3);
    const first = getDictation(level.id, 0);
    const second = getDictation(level.id, 1);
    const afterCycle = getDictation(level.id, level.dictations.length);
    assert.notEqual(first.text, second.text);
    assert.equal(afterCycle.index, 0);
    assert.equal(afterCycle.text, first.text);
  }
});

test("keeps the score between zero and one hundred", () => {
  assert.equal(calculateScore("Un joli mot", 12000), calculateScore("Douze mots", 12000));
  assert.equal(calculateScore("Le chat dort", 30000), 27);
  assert.ok(calculateScore("Un challenge plus long", 30000) > calculateScore("Un mot", 30000));
  assert.ok(calculateScore("Le chat dort", 30000, 1) < calculateScore("Le chat dort", 30000));
  assert.equal(calculateScore("", 30000), 0);
  assert.equal(calculateScore("Le chat dort", 1), 100);
  assert.ok(calculateScore("Le chat dort", 30000) >= 0 && calculateScore("Le chat dort", 30000) <= 100);
});

test("assigns stars and awards at the requested score thresholds", () => {
  assert.deepEqual(getScoreReward(19), { stars: 0, badge: "none" });
  assert.deepEqual(getScoreReward(20), { stars: 1, badge: "none" });
  assert.deepEqual(getScoreReward(40), { stars: 2, badge: "none" });
  assert.deepEqual(getScoreReward(60), { stars: 3, badge: "none" });
  assert.deepEqual(getScoreReward(70), { stars: 3, badge: "bronze" });
  assert.deepEqual(getScoreReward(80), { stars: 3, badge: "silver" });
  assert.deepEqual(getScoreReward(90), { stars: 3, badge: "gold" });
  assert.deepEqual(getScoreReward(100), { stars: 3, badge: "trophy" });
});

test("keeps the leaderboard ordered and capped", () => {
  const entries = sortLeaderboard([
    { id: "low", score: 20, createdAt: 1 },
    { id: "high", score: 90, createdAt: 2 },
    { id: "middle", score: 40, createdAt: 3 },
    { id: "legacy", score: 820, createdAt: 4 },
  ]);

  assert.deepEqual(entries.map((entry) => entry.id), ["high", "middle", "low"]);
});

test("splits French text without losing words or punctuation", () => {
  const source = "Le petit chat dort. Puis il se réveille, et regarde dehors.";
  const fragments = splitTextIntoFragments(source, {
    targetLetters: 15,
  });

  assert.equal(fragments.join(" "), source);
  assert.equal(fragments[0], "Le petit chat dort.");
  assert.ok(fragments.every((fragment, index) => index === fragments.length - 1 || countLetters(fragment) >= 15));
});

test("rounds up to a word without crossing two sentences", () => {
  const fragments = splitTextIntoFragments("Le chat dort. Puis il joue dans le jardin. Le soleil brille.", {
    targetLetters: 8,
  });

  assert.deepEqual(fragments, [
    "Le chat dort.",
    "Puis il joue",
    "dans le jardin.",
    "Le soleil",
    "brille.",
  ]);
});

test("runs a session and counts reviews immutably", () => {
  const initial = createSession(exercise, "session-1", "manual");
  const ready = transitionSession(initial, { type: "USE_MANUAL_MODE" });
  const memorizing = transitionSession(ready, {
    type: "START",
    startedAt: "2026-08-01T10:00:00.000Z",
  });
  const decision = transitionSession(memorizing, { type: "LOOKED_AWAY" });
  const reviewed = transitionSession(decision, { type: "REVIEW" });

  assert.equal(reviewed.status, "memorizing");
  assert.equal(totalReviews(reviewed.result.reviewCounts), 1);
  assert.equal(totalReviews(decision.result.reviewCounts), 0);
});

test("finishes on continue from the final fragment", () => {
  let state = createSession({ ...exercise, fragments: ["Le chat dort."] }, "session-2", "manual");
  state = transitionSession(state, { type: "USE_MANUAL_MODE" });
  state = transitionSession(state, { type: "START", startedAt: "start" });
  state = transitionSession(state, { type: "LOOKED_AWAY" });
  state = transitionSession(state, { type: "CONTINUE", completedAt: "end" });

  assert.equal(state.status, "summary");
  assert.equal(state.result.completedAt, "end");
});

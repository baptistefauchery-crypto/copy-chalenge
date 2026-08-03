import assert from "node:assert/strict";
import test from "node:test";

import {
  countLetters,
  calculateScore,
  calculateScoreBreakdown,
  createSession,
  getDictation,
  PRIMARY_LEVELS,
  getScoreReward,
  splitTextIntoFragmentDetails,
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
  assert.deepEqual(PRIMARY_LEVELS.map((level) => level.label), [
    "CP — Cours préparatoire",
    "CE1 — Cours élémentaire 1re année",
    "Niveau intermédiaire",
    "Niveau avancé",
    "Perfectionnement",
  ]);
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

test("calculates the multi-factor score between zero and one hundred", () => {
  const perfect = {
    spellingFaults: 0,
    wordCount: 3,
    ocrConfidence: 0.91,
    speedReferenceLettersPerSecond: 2,
  };

  assert.equal(calculateScore("Le chat dort", 5000, 0, perfect), 100);
  assert.equal(calculateScore("Le chat dort", 5000, 0, { ...perfect, spellingFaults: 1 }), 88);
  assert.equal(calculateScore("Le chat dort", 5000, 1, perfect), 82);
  assert.equal(calculateScore("Le chat dort", 10000, 0, perfect), 99);
  assert.equal(calculateScore("Le chat dort", 5000, 0, { ...perfect, spellingFaults: 3, wordCount: 3 }), 58);
  assert.equal(calculateScore("", 30000, 0, perfect), 0);
  assert.ok(calculateScore("Le chat dort", 30000, 0, perfect) >= 0);
  assert.ok(calculateScore("Le chat dort", 30000, 0, perfect) <= 100);
});

test("keeps a raw score above one hundred before applying the display cap", () => {
  const longText = "a".repeat(250);
  const result = calculateScoreBreakdown(longText, 250_000 / 3, 0, {
    spellingFaults: 0,
    wordCount: 25,
    ocrConfidence: 1,
    speedReferenceLettersPerSecond: 2,
  });

  assert.equal(result.rawScore, 112);
  assert.equal(result.score, 100);
  assert.equal(result.lengthBonus, 5);
  assert.equal(result.speedAdjustment, 6);
  assert.equal(result.confidenceAdjustment, 1);
});

test("allows one hundred without perfect speed or OCR confidence", () => {
  const result = calculateScoreBreakdown("abcdefghij", 20_000 / 3, 0, {
    spellingFaults: 0,
    wordCount: 1,
    ocrConfidence: 0.75,
    speedReferenceLettersPerSecond: 2,
  });

  assert.ok(Math.abs(result.rawScore - 100) < 1e-9);
  assert.equal(result.score, 100);
});

test("weights reviews above faults, speed, and OCR confidence", () => {
  const text = "a".repeat(50);
  const elapsedAtExpectedSpeed = 100_000 / 3;
  const options = {
    spellingFaults: 0,
    wordCount: 5,
    ocrConfidence: 0.75,
    speedReferenceLettersPerSecond: 2,
  };
  const baseline = calculateScoreBreakdown(text, elapsedAtExpectedSpeed, 0, options);
  const oneReview = calculateScoreBreakdown(text, elapsedAtExpectedSpeed, 1, options);
  const oneFault = calculateScoreBreakdown(text, elapsedAtExpectedSpeed, 0, {
    ...options,
    spellingFaults: 1,
  });
  const slowest = calculateScoreBreakdown(text, Number.MAX_VALUE, 0, options);
  const unreadable = calculateScoreBreakdown(text, elapsedAtExpectedSpeed, 0, {
    ...options,
    ocrConfidence: 0,
  });

  assert.ok(Math.abs(baseline.rawScore - 100) < 1e-9);
  assert.ok(baseline.rawScore - oneReview.rawScore > baseline.rawScore - oneFault.rawScore);
  assert.ok(baseline.rawScore - oneFault.rawScore > baseline.rawScore - slowest.rawScore);
  assert.ok(baseline.rawScore - slowest.rawScore > baseline.rawScore - unreadable.rawScore);
  assert.equal(oneReview.reviewMultiplier, 0.8);
  assert.equal(oneFault.faultPenalty, 9);
  assert.equal(slowest.speedAdjustment, -6);
  assert.equal(unreadable.confidenceAdjustment, -3);
});

test("bounds malformed score inputs deterministically", () => {
  const malformed = calculateScoreBreakdown("abcdefghij", Number.NaN, -4, {
    spellingFaults: Number.POSITIVE_INFINITY,
    wordCount: Number.NaN,
    ocrConfidence: Number.NaN,
    speedReferenceLettersPerSecond: Number.NaN,
  });

  assert.ok(Number.isFinite(malformed.rawScore));
  assert.ok(malformed.score >= 0 && malformed.score <= 100);
  assert.equal(malformed.reviewMultiplier, 1);
  assert.equal(malformed.confidenceAdjustment, -3);
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

test("keeps requested French determiners with the following word", () => {
  const fragments = splitTextIntoFragments(
    "Voir le grand Corbeau et la Fourmi avec un Renard, une Cigale, des graines, les bois, au matin et aux champs.",
    { targetLetters: 4 },
  );
  const forbiddenEnd = /(?:^|\s)(?:le|la|l[’']|un|une|des|les|au|aux)$/iu;

  assert.ok(fragments.length > 2);
  assert.ok(fragments.every((fragment) => !forbiddenEnd.test(fragment)));
  assert.equal(fragments.join(" "), "Voir le grand Corbeau et la Fourmi avec un Renard, une Cigale, des graines, les bois, au matin et aux champs.");
});

test("publishes La Fontaine poems with faithful verse metadata", () => {
  const advanced = getDictation("CM1", 0);
  assert.match(advanced.text, /^La Cigale, ayant chanté\nTout l’été,/u);
  assert.match(advanced.text, /Chez la Fourmi sa voisine,/u);
  assert.equal(advanced.fragmentMode, "letters");
  assert.equal(advanced.preserveVerseBreaks, true);

  const corbeau = getDictation("CM2", 0);
  const loup = getDictation("CM2", 1);
  assert.match(corbeau.text, /^Maître Corbeau,/u);
  assert.match(corbeau.text, /Le Renard s’en saisit/u);
  assert.match(loup.text, /^La raison du plus fort/u);
  assert.match(loup.text, /Le Loup l’emporte/u);
  assert.equal(corbeau.fragmentMode, "verses");
  assert.equal(loup.fragmentMode, "verses");
});

test("splits perfectionnement poems verse by verse and exposes verse ends", () => {
  const dictation = getDictation("CM2", 0);
  const details = splitTextIntoFragmentDetails(dictation.text, {
    targetLetters: 1,
    mode: dictation.fragmentMode,
    preserveVerseBreaks: dictation.preserveVerseBreaks,
  });

  assert.equal(details.length, dictation.text.split("\n").length);
  assert.deepEqual(details.slice(0, 3), [
    { text: "Maître Corbeau, sur un arbre perché,", endsVerse: true },
    { text: "Tenait en son bec un fromage.", endsVerse: true },
    { text: "Maître Renard, par l’odeur alléché,", endsVerse: true },
  ]);
});

test("preserves verse boundaries while using letter-sized advanced fragments", () => {
  const dictation = getDictation("CM1", 0);
  const details = splitTextIntoFragmentDetails(dictation.text, {
    targetLetters: 8,
    mode: dictation.fragmentMode,
    preserveVerseBreaks: dictation.preserveVerseBreaks,
  });

  assert.deepEqual(details.slice(0, 3), [
    { text: "La Cigale,", endsVerse: false },
    { text: "ayant chanté", endsVerse: true },
    { text: "Tout l’été,", endsVerse: true },
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

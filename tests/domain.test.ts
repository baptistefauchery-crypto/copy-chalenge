import assert from "node:assert/strict";
import test from "node:test";

import {
  createSession,
  splitTextIntoFragments,
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

test("splits French text without losing words or punctuation", () => {
  const source = "Le petit chat dort. Puis il se réveille, et regarde dehors.";
  const fragments = splitTextIntoFragments(source, {
    minWords: 3,
    targetWords: 4,
    maxWords: 6,
  });

  assert.equal(fragments.join(" "), source);
  assert.equal(fragments[0], "Le petit chat dort.");
  assert.ok(fragments.every((fragment) => fragment.split(" ").length <= 6));
});

test("runs a session and counts reviews immutably", () => {
  const initial = createSession(exercise, "session-1", "manual");
  const ready = transitionSession(initial, { type: "USE_MANUAL_MODE" });
  const memorizing = transitionSession(ready, {
    type: "START",
    startedAt: "2026-08-01T10:00:00.000Z",
  });
  const writing = transitionSession(memorizing, { type: "LOOKED_AWAY" });
  const decision = transitionSession(writing, { type: "LOOKED_BACK" });
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
  state = transitionSession(state, { type: "LOOKED_BACK" });
  state = transitionSession(state, { type: "CONTINUE", completedAt: "end" });

  assert.equal(state.status, "summary");
  assert.equal(state.result.completedAt, "end");
});

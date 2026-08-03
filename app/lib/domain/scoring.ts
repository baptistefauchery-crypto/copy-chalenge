export interface LeaderboardEntry {
  id: string;
  score: number;
  createdAt: number;
}

export const LEADERBOARD_STORAGE_KEY = "copy-challenge-leaderboard-v1";
export const MAX_SCORE = 100;
const LEADERBOARD_LIMIT = 5;
export const SCORE_BASE = 110;
export const SCORE_FAULT_WEIGHT = 0.6;
export const SCORE_CONFIDENCE_WEIGHT = 0.2;
export const SCORE_SPEED_WEIGHT = 0.2;
export const SPELLING_PENALTY_MULTIPLIER = 2;
const REVIEW_SCORE_MULTIPLIER = 0.8;

export interface ScoreCalculationOptions {
  spellingFaults: number;
  wordCount: number;
  ocrConfidence: number;
  speedReferenceLettersPerSecond: number;
}

export type ScoreBadge = "none" | "bronze" | "silver" | "gold" | "trophy";

export interface ScoreReward {
  stars: 0 | 1 | 2 | 3;
  badge: ScoreBadge;
}

export function countScoringLetters(text: string): number {
  return text.match(/\p{L}/gu)?.length ?? 0;
}

export function countScoringWords(text: string): number {
  return text.match(/[\p{L}\p{M}\p{N}]+(?:['’][\p{L}\p{M}\p{N}]+)*/gu)?.length ?? 0;
}

export function calculateScore(
  text: string,
  elapsedMs: number,
  reviewCount = 0,
  options: ScoreCalculationOptions,
): number {
  const letters = countScoringLetters(text);
  if (letters === 0) return 0;

  const elapsedSeconds = Math.max(Number.isFinite(elapsedMs) ? elapsedMs / 1000 : 1, 1);
  const reviews = Number.isFinite(reviewCount) ? Math.max(0, reviewCount) : 0;
  const wordCount = Math.max(1, Number.isFinite(options.wordCount) ? Math.round(options.wordCount) : countScoringWords(text));
  const spellingFaults = Number.isFinite(options.spellingFaults)
    ? Math.max(0, options.spellingFaults)
    : wordCount;
  const spellingScore = Math.max(0, 1 - SPELLING_PENALTY_MULTIPLIER * spellingFaults / wordCount);
  const confidenceScore = clampUnit(options.ocrConfidence);
  const speedReference = Math.max(options.speedReferenceLettersPerSecond, Number.EPSILON);
  const lettersPerSecond = letters / elapsedSeconds;
  const speedScore = Math.min(1, lettersPerSecond / speedReference);
  const reviewMultiplier = Math.pow(REVIEW_SCORE_MULTIPLIER, reviews);
  const rawScore = SCORE_BASE * (
    SCORE_FAULT_WEIGHT * spellingScore
    + SCORE_CONFIDENCE_WEIGHT * confidenceScore
    + SCORE_SPEED_WEIGHT * speedScore
  ) * reviewMultiplier;
  return Math.min(MAX_SCORE, Math.max(0, Math.round(rawScore)));
}

function clampUnit(value: number): number {
  return Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0;
}

export function getScoreReward(score: number): ScoreReward {
  const normalizedScore = Math.min(MAX_SCORE, Math.max(0, Math.round(score)));

  return {
    stars: normalizedScore >= 60 ? 3 : normalizedScore >= 40 ? 2 : normalizedScore >= 20 ? 1 : 0,
    badge: normalizedScore >= 100
      ? "trophy"
      : normalizedScore >= 90
        ? "gold"
        : normalizedScore >= 80
          ? "silver"
          : normalizedScore >= 70
            ? "bronze"
            : "none",
  };
}

export function sortLeaderboard(entries: readonly LeaderboardEntry[]): LeaderboardEntry[] {
  return entries
    .filter((entry) => Number.isFinite(entry.score) && entry.score >= 0 && entry.score <= MAX_SCORE && Number.isFinite(entry.createdAt))
    .sort((left, right) => right.score - left.score || left.createdAt - right.createdAt)
    .slice(0, LEADERBOARD_LIMIT);
}

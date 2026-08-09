export interface LeaderboardEntry {
  id: string;
  score: number;
  createdAt: number;
}

export const LEADERBOARD_STORAGE_KEY = "copy-challenge-leaderboard-v1";
export const MAX_SCORE = 100;
const LEADERBOARD_LIMIT = 5;
export const SCORE_POINTS_PER_LETTER = 80;
export const DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND = 2;
export const REVIEW_SCORE_MULTIPLIER = 0.8;

export interface ScoreCalculationOptions {
  speedReferenceLettersPerSecond: number;
}

export interface ScoreBreakdown {
  score: number;
  rawScore: number;
  baseScore: number;
  speedAdjustment: number;
  lengthBonus: number;
  reviewMultiplier: number;
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
  options: ScoreCalculationOptions = { speedReferenceLettersPerSecond: DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND },
): number {
  return calculateScoreBreakdown(text, elapsedMs, reviewCount, options).score;
}

/**
 * Shared score contract: letters copied per second, with a 20% compounding
 * penalty for each review. The level-specific speed reference is deliberately
 * not used: the result must mean the same thing on the web and on Android.
 */
export function calculateScoreBreakdown(
  text: string,
  elapsedMs: number,
  reviewCount = 0,
  _options: ScoreCalculationOptions,
): ScoreBreakdown {
  void _options;
  const letters = countScoringLetters(text);
  if (letters === 0) {
    return {
      score: 0,
      rawScore: 0,
      baseScore: 0,
      speedAdjustment: 0,
      lengthBonus: 0,
      reviewMultiplier: 1,
    };
  }

  const elapsedSeconds = Math.max(Number.isFinite(elapsedMs) ? elapsedMs / 1000 : 1, 1);
  const reviews = Number.isFinite(reviewCount) ? Math.max(0, Math.floor(reviewCount)) : 0;
  const reviewMultiplier = Math.pow(REVIEW_SCORE_MULTIPLIER, reviews);
  const baseScore = (letters * SCORE_POINTS_PER_LETTER) / elapsedSeconds;
  const rawScore = baseScore * reviewMultiplier;

  return {
    score: Math.min(MAX_SCORE, Math.max(0, Math.round(rawScore))),
    rawScore,
    baseScore,
    speedAdjustment: 0,
    lengthBonus: 0,
    reviewMultiplier,
  };
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

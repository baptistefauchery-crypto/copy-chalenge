export interface LeaderboardEntry {
  id: string;
  score: number;
  createdAt: number;
}

export const LEADERBOARD_STORAGE_KEY = "copy-challenge-leaderboard-v1";
export const MAX_SCORE = 100;
const LEADERBOARD_LIMIT = 5;
export const SCORE_BASE = 100;
export const MAX_FAULT_PENALTY = 45;
export const MAX_SPEED_ADJUSTMENT = 6;
export const MAX_LENGTH_BONUS = 5;
export const MIN_LENGTH_FOR_BONUS = 50;
export const LENGTH_FOR_MAX_BONUS = 250;
export const EXPECTED_SPEED_RATIO = 0.75;
export const EXPECTED_OCR_CONFIDENCE = 0.75;
export const OCR_CONFIDENCE_SCALE = 4;
export const DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND = 2;
const REVIEW_SCORE_MULTIPLIER = 0.8;

export interface ScoreCalculationOptions {
  spellingFaults: number;
  wordCount: number;
  ocrConfidence: number;
  speedReferenceLettersPerSecond: number;
}

export interface ScoreBreakdown {
  score: number;
  rawScore: number;
  baseScore: number;
  faultPenalty: number;
  speedAdjustment: number;
  lengthBonus: number;
  confidenceAdjustment: number;
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
  options: ScoreCalculationOptions,
): number {
  return calculateScoreBreakdown(text, elapsedMs, reviewCount, options).score;
}

/**
 * Calculates a deliberately readable score before capping it for display.
 *
 * - 100 points is the neutral target: no faults, 75% of the reference speed,
 *   and 75% OCR confidence. Perfect speed/readability are therefore not needed.
 * - Reviews have the largest normal impact (20% compounding penalty each).
 * - Faults remove up to 45 points, proportionally to the expected word count.
 * - Speed changes at most +/-6 points and is based on letters per second.
 * - Long texts add at most 5 points; OCR confidence changes -3 to +1 point.
 *
 * A flawless long and fast dictation can reach 112 raw points. `score` is the
 * rounded 0..100 value used by the UI, while `rawScore` remains available for
 * diagnostics and future reward tuning.
 */
export function calculateScoreBreakdown(
  text: string,
  elapsedMs: number,
  reviewCount = 0,
  options: ScoreCalculationOptions,
): ScoreBreakdown {
  const letters = countScoringLetters(text);
  if (letters === 0) {
    return {
      score: 0,
      rawScore: 0,
      baseScore: SCORE_BASE,
      faultPenalty: 0,
      speedAdjustment: 0,
      lengthBonus: 0,
      confidenceAdjustment: 0,
      reviewMultiplier: 1,
    };
  }

  const elapsedSeconds = Math.max(Number.isFinite(elapsedMs) ? elapsedMs / 1000 : 1, 1);
  const reviews = Number.isFinite(reviewCount) ? Math.max(0, Math.floor(reviewCount)) : 0;
  const wordCount = Math.max(1, Number.isFinite(options.wordCount) ? Math.round(options.wordCount) : countScoringWords(text));
  const spellingFaults = Number.isFinite(options.spellingFaults)
    ? Math.max(0, Math.round(options.spellingFaults))
    : wordCount;
  const faultRate = clampUnit(spellingFaults / wordCount);
  const faultPenalty = MAX_FAULT_PENALTY * faultRate;
  const confidence = clampUnit(options.ocrConfidence);
  const confidenceAdjustment = OCR_CONFIDENCE_SCALE * (confidence - EXPECTED_OCR_CONFIDENCE);
  const speedReference = Number.isFinite(options.speedReferenceLettersPerSecond)
    ? Math.max(options.speedReferenceLettersPerSecond, Number.EPSILON)
    : DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND;
  const lettersPerSecond = letters / elapsedSeconds;
  const speedRatio = lettersPerSecond / speedReference;
  const normalizedSpeed = clampSigned((speedRatio - EXPECTED_SPEED_RATIO) / EXPECTED_SPEED_RATIO);
  const speedAdjustment = MAX_SPEED_ADJUSTMENT * normalizedSpeed;
  const lengthProgress = (letters - MIN_LENGTH_FOR_BONUS) / (LENGTH_FOR_MAX_BONUS - MIN_LENGTH_FOR_BONUS);
  const lengthBonus = MAX_LENGTH_BONUS * clampUnit(lengthProgress);
  const reviewMultiplier = Math.pow(REVIEW_SCORE_MULTIPLIER, reviews);
  const rawScore = Math.max(
    0,
    SCORE_BASE - faultPenalty + speedAdjustment + lengthBonus + confidenceAdjustment,
  ) * reviewMultiplier;

  return {
    score: Math.min(MAX_SCORE, Math.max(0, Math.round(rawScore))),
    rawScore,
    baseScore: SCORE_BASE,
    faultPenalty,
    speedAdjustment,
    lengthBonus,
    confidenceAdjustment,
    reviewMultiplier,
  };
}

function clampUnit(value: number): number {
  return Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0;
}

function clampSigned(value: number): number {
  return Number.isFinite(value) ? Math.min(1, Math.max(-1, value)) : -1;
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

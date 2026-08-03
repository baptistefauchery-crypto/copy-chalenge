export interface LeaderboardEntry {
  id: string;
  score: number;
  createdAt: number;
}

export const LEADERBOARD_STORAGE_KEY = "copy-challenge-leaderboard-v1";
export const MAX_SCORE = 100;
const LEADERBOARD_LIMIT = 5;
const SCORE_POINTS_PER_LETTER = 80;
const REVIEW_SCORE_MULTIPLIER = 0.8;

export type ScoreBadge = "none" | "bronze" | "silver" | "gold" | "trophy";

export interface ScoreReward {
  stars: 0 | 1 | 2 | 3;
  badge: ScoreBadge;
}

export function countScoringLetters(text: string): number {
  return text.match(/\p{L}/gu)?.length ?? 0;
}

export function calculateScore(text: string, elapsedMs: number, reviewCount = 0): number {
  const letters = countScoringLetters(text);
  if (letters === 0) return 0;

  const elapsedSeconds = Math.max(Number.isFinite(elapsedMs) ? elapsedMs / 1000 : 1, 1);
  const reviews = Number.isFinite(reviewCount) ? Math.max(0, reviewCount) : 0;
  const reviewMultiplier = Math.pow(REVIEW_SCORE_MULTIPLIER, reviews);
  const rawScore = (letters * SCORE_POINTS_PER_LETTER * reviewMultiplier) / elapsedSeconds;
  return Math.min(MAX_SCORE, Math.max(0, Math.round(rawScore)));
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

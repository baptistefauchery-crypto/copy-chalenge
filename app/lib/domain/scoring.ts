export interface LeaderboardEntry {
  id: string;
  score: number;
  createdAt: number;
}

export const LEADERBOARD_STORAGE_KEY = "copy-challenge-leaderboard-v1";
const LEADERBOARD_LIMIT = 5;
const SCORE_SCALE = 700;

export function countScoringLetters(text: string): number {
  return text.match(/\p{L}/gu)?.length ?? 0;
}

export function calculateScore(text: string, elapsedMs: number, reviewCount = 0): number {
  const letters = countScoringLetters(text);
  if (letters === 0) return 0;

  const elapsedSeconds = Math.max(elapsedMs / 1000, 1);
  const lengthMultiplier = 1 + letters * 0.001;
  const reviewMultiplier = Math.pow(0.85, Math.max(0, reviewCount));
  return Math.max(1, Math.round((SCORE_SCALE * letters * lengthMultiplier * reviewMultiplier) / elapsedSeconds));
}

export function sortLeaderboard(entries: readonly LeaderboardEntry[]): LeaderboardEntry[] {
  return entries
    .filter((entry) => Number.isFinite(entry.score) && entry.score > 0 && Number.isFinite(entry.createdAt))
    .sort((left, right) => right.score - left.score || left.createdAt - right.createdAt)
    .slice(0, LEADERBOARD_LIMIT);
}

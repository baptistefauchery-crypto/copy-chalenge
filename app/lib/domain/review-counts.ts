export function createReviewCounts(fragmentCount: number): number[] {
  if (!Number.isInteger(fragmentCount) || fragmentCount < 0) {
    throw new RangeError("fragmentCount must be a non-negative integer");
  }

  return Array.from({ length: fragmentCount }, () => 0);
}

export function incrementReviewCount(
  counts: readonly number[],
  fragmentIndex: number,
): number[] {
  if (!Number.isInteger(fragmentIndex) || fragmentIndex < 0 || fragmentIndex >= counts.length) {
    throw new RangeError("fragmentIndex is outside review counts");
  }

  return counts.map((count, index) => index === fragmentIndex ? count + 1 : count);
}

export function totalReviews(counts: readonly number[]): number {
  return counts.reduce((total, count) => total + count, 0);
}

export function reviewedFragmentCount(counts: readonly number[]): number {
  return counts.filter((count) => count > 0).length;
}


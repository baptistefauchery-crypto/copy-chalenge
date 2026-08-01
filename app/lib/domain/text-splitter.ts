export interface TextSplitOptions {
  /** Desired fragment size in letters. The fragment ends at a word boundary and never crosses a sentence. */
  targetLetters?: number;
}

function normalizeOptions(options: TextSplitOptions) {
  const targetLetters = options.targetLetters ?? 30;

  if (!Number.isInteger(targetLetters)) {
    throw new TypeError("Fragment sizes must be integers");
  }
  if (targetLetters < 1) {
    throw new RangeError("targetLetters must be a positive integer");
  }

  return { targetLetters };
}

function endsSentence(word: string): boolean {
  return /[.!?](?:[»"')\]]*)$/u.test(word);
}

/** Counts letters while ignoring spaces, punctuation, and numbers. */
export function countLetters(text: string): number {
  return Array.from(text.matchAll(/\p{L}/gu)).length;
}

function chooseSize(
  words: readonly string[],
  offset: number,
  targetLetters: number,
): number {
  const remaining = words.length - offset;
  let letters = 0;

  for (let size = 1; size <= remaining; size += 1) {
    const word = words[offset + size - 1];
    letters += countLetters(word);
    if (endsSentence(word)) return size;
    if (letters >= targetLetters) return size;
  }

  return remaining;
}

/**
 * Splits French prose into readable fragments of roughly the requested number
 * of letters. Each fragment is rounded up to the next complete word, without
 * crossing a sentence boundary, while
 * preserving punctuation. Whitespace is normalized and an empty input produces [].
 */
export function splitTextIntoFragments(
  text: string,
  options: TextSplitOptions = {},
): string[] {
  const normalized = text.trim().replace(/\s+/gu, " ");
  if (!normalized) return [];

  const config = normalizeOptions(options);
  const words = normalized.split(" ");
  const fragments: string[] = [];

  for (let offset = 0; offset < words.length;) {
    const size = chooseSize(
      words,
      offset,
      config.targetLetters,
    );
    fragments.push(words.slice(offset, offset + size).join(" "));
    offset += size;
  }

  return fragments;
}

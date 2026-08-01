export interface TextSplitOptions {
  /** Desired fragment size. Defaults to 5 words. */
  targetWords?: number;
  /** Smallest fragment size, except when the whole remaining text is shorter. */
  minWords?: number;
  /** Hard upper bound. Defaults to 7 words. */
  maxWords?: number;
  /** Prefer sentence and clause endings close to the target. Defaults to true. */
  preferPunctuation?: boolean;
}

const STRONG_END = /[.!?…][”»)]*$/u;
const SOFT_END = /[,;:][”»)]*$/u;
const WEAK_END_WORDS = new Set([
  "à", "au", "aux", "avec", "ce", "ces", "cette", "de", "des", "du",
  "en", "et", "la", "le", "les", "mais", "ou", "par", "pour", "sans",
  "sous", "sur", "un", "une",
]);

function normalizeOptions(options: TextSplitOptions) {
  const targetWords = options.targetWords ?? 5;
  const minWords = options.minWords ?? 3;
  const maxWords = options.maxWords ?? 7;

  if (![targetWords, minWords, maxWords].every(Number.isInteger)) {
    throw new TypeError("Fragment sizes must be integers");
  }
  if (minWords < 1 || targetWords < minWords || maxWords < targetWords) {
    throw new RangeError("Expected 1 <= minWords <= targetWords <= maxWords");
  }

  return {
    targetWords,
    minWords,
    maxWords,
    preferPunctuation: options.preferPunctuation ?? true,
  };
}

function cleanEndingWord(token: string): string {
  return token
    .toLocaleLowerCase("fr")
    .replace(/^[«“(]+|[.,;:!?…»”')]+$/gu, "");
}

function chooseSize(
  words: readonly string[],
  offset: number,
  minWords: number,
  targetWords: number,
  maxWords: number,
  preferPunctuation: boolean,
): number {
  const remaining = words.length - offset;
  if (remaining <= maxWords) return remaining;

  const upper = Math.min(maxWords, remaining - minWords);
  const lower = Math.min(minWords, upper);

  if (preferPunctuation) {
    for (let size = upper; size >= lower; size -= 1) {
      if (STRONG_END.test(words[offset + size - 1])) return size;
    }
    for (let distance = 0; distance <= maxWords; distance += 1) {
      for (const size of [targetWords - distance, targetWords + distance]) {
        if (size >= lower && size <= upper && SOFT_END.test(words[offset + size - 1])) {
          return size;
        }
      }
    }
  }

  for (let size = Math.min(targetWords, upper); size >= lower; size -= 1) {
    if (!WEAK_END_WORDS.has(cleanEndingWord(words[offset + size - 1]))) return size;
  }

  return Math.min(targetWords, upper);
}

/**
 * Splits French prose into readable word groups while preserving the original
 * punctuation. Whitespace is normalized and an empty input produces [].
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
      config.minWords,
      config.targetWords,
      config.maxWords,
      config.preferPunctuation,
    );
    fragments.push(words.slice(offset, offset + size).join(" "));
    offset += size;
  }

  return fragments;
}


import type { FragmentMode, TextFragment } from "./types";

export interface TextSplitOptions {
  /** Desired fragment size in letters. The fragment ends at a word boundary. */
  targetLetters?: number;
  /** Perfectionnement poems use one complete verse per fragment. */
  mode?: FragmentMode;
  /** Preserve verse boundaries and expose their ends even in letter mode. */
  preserveVerseBreaks?: boolean;
}

const LINKED_DETERMINERS = new Set([
  "le",
  "la",
  "l'",
  "l’",
  "un",
  "une",
  "des",
  "les",
  "au",
  "aux",
]);

function normalizeOptions(options: TextSplitOptions) {
  const targetLetters = options.targetLetters ?? 30;

  if (!Number.isInteger(targetLetters)) {
    throw new TypeError("Fragment sizes must be integers");
  }
  if (targetLetters < 1) {
    throw new RangeError("targetLetters must be a positive integer");
  }

  return {
    targetLetters,
    mode: options.mode ?? "letters",
    preserveVerseBreaks: options.preserveVerseBreaks ?? false,
  };
}

function endsSentence(word: string): boolean {
  return /[.!?](?:[»"')\]]*)$/u.test(word);
}

function isLinkedDeterminer(word: string): boolean {
  const normalized = word
    .toLocaleLowerCase("fr-FR")
    .replace(/^[«“"(\[]+/u, "")
    .replace(/[,;:!?.”»")\]]+$/u, "");
  return LINKED_DETERMINERS.has(normalized);
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
    const reachedBoundary = endsSentence(word) || letters >= targetLetters;
    if (!reachedBoundary) continue;

    // A determiner is pedagogically meaningless on its own: always keep it
    // with the word that follows, even when the target was just reached.
    if (isLinkedDeterminer(word) && size < remaining) continue;
    return size;
  }

  return remaining;
}

function splitLineByLetters(line: string, targetLetters: number): string[] {
  const normalized = line.trim().replace(/[\t ]+/gu, " ");
  if (!normalized) return [];

  const words = normalized.split(" ");
  const fragments: string[] = [];
  for (let offset = 0; offset < words.length;) {
    const size = chooseSize(words, offset, targetLetters);
    fragments.push(words.slice(offset, offset + size).join(" "));
    offset += size;
  }
  return fragments;
}

/**
 * Produces fragment text plus verse-boundary metadata. The marker is data, not
 * punctuation so the displayed text remains faithful to the source.
 */
export function splitTextIntoFragmentDetails(
  text: string,
  options: TextSplitOptions = {},
): TextFragment[] {
  const config = normalizeOptions(options);

  if (config.mode === "verses") {
    return text
      .split(/\r?\n/gu)
      .map((line) => line.trim().replace(/[\t ]+/gu, " "))
      .filter(Boolean)
      .map((line) => ({ text: line, endsVerse: true }));
  }

  if (config.preserveVerseBreaks) {
    return text
      .split(/\r?\n/gu)
      .map((line) => splitLineByLetters(line, config.targetLetters))
      .filter((fragments) => fragments.length > 0)
      .flatMap((fragments) => fragments.map((fragment, index) => ({
        text: fragment,
        endsVerse: index === fragments.length - 1,
      })));
  }

  return splitLineByLetters(text.trim().replace(/\s+/gu, " "), config.targetLetters)
    .map((fragment) => ({ text: fragment, endsVerse: false }));
}

/**
 * Splits French text into display fragments while preserving punctuation and
 * never leaving le/la/l’/un/une/des/les/au/aux at the end of a fragment.
 */
export function splitTextIntoFragments(
  text: string,
  options: TextSplitOptions = {},
): string[] {
  return splitTextIntoFragmentDetails(text, options).map(({ text: fragment }) => fragment);
}

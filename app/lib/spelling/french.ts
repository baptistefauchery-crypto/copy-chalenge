import NSpell from "nspell";

export interface SpellingIssue {
  word: string;
  tokenIndex: number;
  suggestions: string[];
}

export interface FrenchSpellingResult {
  text: string;
  checkedWords: number;
  issues: SpellingIssue[];
  dictionary: "fr";
}

interface SpellChecker {
  correct(word: string): boolean;
  suggest(word: string): string[];
}

interface SpellDictionary {
  aff: string;
  dic: string;
}

type SpellCheckerConstructor = new (dictionary: SpellDictionary) => SpellChecker;

const AFFIX_URL = "/dictionaries/fr/index.aff";
const DICTIONARY_URL = "/dictionaries/fr/index.dic";
const WORD_PATTERN = /[\p{L}\p{M}]+(?:[’'][\p{L}\p{M}]+)*/gu;
const spellCheckerConstructor = NSpell as unknown as SpellCheckerConstructor;

let checkerPromise: Promise<SpellChecker> | null = null;

async function loadDictionaryFile(url: string) {
  const response = await fetch(url);
  if (!response.ok) throw new Error("Le dictionnaire français est indisponible (" + response.status + ").");
  return response.text();
}

async function createSpellChecker() {
  const [aff, dic] = await Promise.all([
    loadDictionaryFile(AFFIX_URL),
    loadDictionaryFile(DICTIONARY_URL),
  ]);
  return new spellCheckerConstructor({ aff, dic });
}

function getSpellChecker() {
  if (!checkerPromise) {
    checkerPromise = createSpellChecker().catch((error) => {
      checkerPromise = null;
      throw error;
    });
  }
  return checkerPromise;
}

function getWordParts(word: string) {
  const parts = word.split(/[’']/u);
  return parts.length > 1 ? parts.slice(1) : parts;
}

export async function checkFrenchSpelling(text: string): Promise<FrenchSpellingResult> {
  const checker = await getSpellChecker();
  const words = [...text.matchAll(WORD_PATTERN)].map((match) => match[0]);
  const issues: SpellingIssue[] = [];

  words.forEach((word, tokenIndex) => {
    const incorrectPart = getWordParts(word).find((part) => !checker.correct(part));
    if (!incorrectPart) return;
    issues.push({
      word,
      tokenIndex,
      suggestions: checker.suggest(incorrectPart).slice(0, 3),
    });
  });

  return {
    text,
    checkedWords: words.length,
    issues,
    dictionary: "fr",
  };
}

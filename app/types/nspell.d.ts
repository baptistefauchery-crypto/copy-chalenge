declare module "nspell" {
  interface NSpellDictionary {
    aff: string | Uint8Array;
    dic?: string | Uint8Array;
  }

  interface NSpellInstance {
    correct(word: string): boolean;
    suggest(word: string): string[];
  }

  const NSpell: new (dictionary: NSpellDictionary) => NSpellInstance;
  export default NSpell;
}

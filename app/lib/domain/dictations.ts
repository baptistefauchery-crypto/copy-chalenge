import type { FragmentMode } from "./types";

export interface DictationDefinition {
  text: string;
  fragmentMode: FragmentMode;
  preserveVerseBreaks: boolean;
}

const prose = (text: string): DictationDefinition => ({
  text,
  fragmentMode: "letters",
  preserveVerseBreaks: false,
});

const poem = (
  text: string,
  fragmentMode: FragmentMode,
): DictationDefinition => ({
  text,
  fragmentMode,
  preserveVerseBreaks: true,
});

const LA_CIGALE_ET_LA_FOURMI = `La Cigale, ayant chanté
Tout l’été,
Se trouva fort dépourvue
Quand la bise fut venue :
Pas un seul petit morceau
De mouche ou de vermisseau.
Elle alla crier famine
Chez la Fourmi sa voisine,
La priant de lui prêter
Quelque grain pour subsister
Jusqu’à la saison nouvelle.
« Je vous paierai, lui dit-elle,
Avant l’août, foi d’animal,
Intérêt et principal. »
La Fourmi n’est pas prêteuse :
C’est là son moindre défaut.
« Que faisiez-vous au temps chaud ?
Dit-elle à cette emprunteuse.
— Nuit et jour à tout venant
Je chantais, ne vous déplaise.
— Vous chantiez ? j’en suis fort aise.
Eh bien ! dansez maintenant. »`;

const LE_CORBEAU_ET_LE_RENARD = `Maître Corbeau, sur un arbre perché,
Tenait en son bec un fromage.
Maître Renard, par l’odeur alléché,
Lui tint à peu près ce langage :
« Hé ! bonjour, Monsieur du Corbeau.
Que vous êtes joli ! que vous me semblez beau !
Sans mentir, si votre ramage
Se rapporte à votre plumage,
Vous êtes le Phénix des hôtes de ces bois. »
À ces mots le Corbeau ne se sent pas de joie ;
Et pour montrer sa belle voix,
Il ouvre un large bec, laisse tomber sa proie.
Le Renard s’en saisit, et dit : « Mon bon Monsieur,
Apprenez que tout flatteur
Vit aux dépens de celui qui l’écoute.
Cette leçon vaut bien un fromage, sans doute. »
Le Corbeau, honteux et confus,
Jura, mais un peu tard, qu’on ne l’y prendrait plus.`;

const LE_LOUP_ET_L_AGNEAU = `La raison du plus fort est toujours la meilleure :
Nous l’allons montrer tout à l’heure.
Un Agneau se désaltérait
Dans le courant d’une onde pure.
Un Loup survient à jeun qui cherchait aventure,
Et que la faim en ces lieux attirait.
« Qui te rend si hardi de troubler mon breuvage ?
Dit cet animal plein de rage :
Tu seras châtié de ta témérité.
— Sire, répond l’Agneau, que Votre Majesté
Ne se mette pas en colère ;
Mais plutôt qu’elle considère
Que je me vas désaltérant
Dans le courant,
Plus de vingt pas au-dessous d’Elle ;
Et que par conséquent, en aucune façon,
Je ne puis troubler sa boisson.
— Tu la troubles, reprit cette bête cruelle ;
Et je sais que de moi tu médis l’an passé.
— Comment l’aurais-je fait si je n’étais pas né ?
Reprit l’Agneau ; je tette encor ma mère.
— Si ce n’est toi, c’est donc ton frère.
— Je n’en ai point. — C’est donc quelqu’un des tiens :
Car vous ne m’épargnez guère,
Vous, vos Bergers, et vos Chiens.
On me l’a dit : il faut que je me venge. »
Là-dessus, au fond des forêts
Le Loup l’emporte, et puis le mange,
Sans autre forme de procès.`;

export const PRIMARY_LEVELS = [
  {
    id: "CP",
    label: "CP — Cours préparatoire",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 6,
    referenceSpeedSignsPerMinute: 10,
    dictations: [
      prose("Lina a un vélo. Elle roule dans la cour."),
      prose("Le chat dort sur le tapis. Il rêve d’une souris."),
      prose("Milo joue avec sa balle. Puis il rentre à la maison."),
    ],
  },
  {
    id: "CE1",
    label: "CE1 — Cours élémentaire 1re année",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 8,
    referenceSpeedSignsPerMinute: 24,
    dictations: [
      prose("Les petits lapins mangent des carottes dans le jardin."),
      prose("Ce matin, Zoé prépare son cartable et cherche ses crayons."),
      prose("La pluie tombe, mais les enfants jouent sous le préau."),
    ],
  },
  {
    id: "CE2",
    label: "Niveau intermédiaire",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 10,
    referenceSpeedSignsPerMinute: 34,
    dictations: [
      prose("Hier, les élèves ont planté des graines près de l’école."),
      prose("Le vieux bateau avance lentement entre les rochers."),
      prose("Demain, nous visiterons le musée avec notre classe."),
    ],
  },
  {
    id: "CM1",
    label: "Niveau avancé",
    cycle: "Cycle 3 · consolidation",
    recommendedLetters: 12,
    referenceSpeedSignsPerMinute: 45,
    dictations: [
      poem(LA_CIGALE_ET_LA_FOURMI, "letters"),
      prose("L’année dernière, nous avons découvert un sentier qui longeait la rivière."),
      prose("Mes cousins sont partis tôt, mais ils ont oublié leurs gourdes."),
    ],
  },
  {
    id: "CM2",
    label: "Perfectionnement",
    cycle: "Cycle 3 · consolidation",
    recommendedLetters: 14,
    referenceSpeedSignsPerMinute: 46,
    dictations: [
      poem(LE_CORBEAU_ET_LE_RENARD, "verses"),
      poem(LE_LOUP_ET_L_AGNEAU, "verses"),
      prose("Les exploratrices avaient préparé leurs sacs avant de partir vers les montagnes enneigées."),
    ],
  },
] as const;

export type PrimaryLevel = typeof PRIMARY_LEVELS[number]["id"];

export interface SelectedDictation extends DictationDefinition {
  level: PrimaryLevel;
  index: number;
  total: number;
}

export function getLevel(level: PrimaryLevel) {
  const definition = PRIMARY_LEVELS.find((entry) => entry.id === level);
  if (!definition) throw new RangeError(`Unknown primary level: ${level}`);
  return definition;
}

export function getDictation(level: PrimaryLevel, index = 0): SelectedDictation {
  const definition = getLevel(level);
  const total = definition.dictations.length;
  const normalizedIndex = ((index % total) + total) % total;
  const dictation = definition.dictations[normalizedIndex];

  return {
    level,
    index: normalizedIndex,
    total,
    ...dictation,
  };
}

export const PRIMARY_LEVELS = [
  {
    id: "CP",
    label: "CP — Cours préparatoire",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 6,
    dictations: [
      "Lina a un vélo. Elle roule dans la cour.",
      "Le chat dort sur le tapis. Il rêve d’une souris.",
      "Milo joue avec sa balle. Puis il rentre à la maison.",
    ],
  },
  {
    id: "CE1",
    label: "CE1 — Cours élémentaire 1re année",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 8,
    dictations: [
      "Les petits lapins mangent des carottes dans le jardin.",
      "Ce matin, Zoé prépare son cartable et cherche ses crayons.",
      "La pluie tombe, mais les enfants jouent sous le préau.",
    ],
  },
  {
    id: "CE2",
    label: "CE2 — Cours élémentaire 2e année",
    cycle: "Cycle 2 · apprentissages fondamentaux",
    recommendedLetters: 10,
    dictations: [
      "Hier, les élèves ont planté des graines près de l’école.",
      "Le vieux bateau avance lentement entre les rochers.",
      "Demain, nous visiterons le musée avec notre classe.",
    ],
  },
  {
    id: "CM1",
    label: "CM1 — Cours moyen 1re année",
    cycle: "Cycle 3 · consolidation",
    recommendedLetters: 12,
    dictations: [
      "Quand le vent se lève, les grandes branches bougent et les oiseaux s’envolent.",
      "L’année dernière, nous avons découvert un sentier qui longeait la rivière.",
      "Mes cousins sont partis tôt, mais ils ont oublié leurs gourdes.",
    ],
  },
  {
    id: "CM2",
    label: "CM2 — Cours moyen 2e année",
    cycle: "Cycle 3 · consolidation",
    recommendedLetters: 14,
    dictations: [
      "Après la pluie, les chemins glissants que nous avions suivis brillaient sous les éclaircies.",
      "Si tu prends le temps de relire tes phrases, tu repéreras les accords oubliés.",
      "Les exploratrices avaient préparé leurs sacs avant de partir vers les montagnes enneigées.",
    ],
  },
] as const;

export type PrimaryLevel = typeof PRIMARY_LEVELS[number]["id"];

export interface SelectedDictation {
  level: PrimaryLevel;
  index: number;
  total: number;
  text: string;
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

  return {
    level,
    index: normalizedIndex,
    total,
    text: definition.dictations[normalizedIndex],
  };
}

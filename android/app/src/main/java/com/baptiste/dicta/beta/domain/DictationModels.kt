package com.baptiste.dicta.beta.domain

enum class SchoolLevel(
    val label: String,
    val cycle: String,
    val recommendedLetters: Int,
    val referenceLettersPerSecond: Double,
    val texts: List<String>,
) {
    CP(
        "CP — Cours préparatoire",
        "Cycle 2 · apprentissages fondamentaux",
        6,
        10.0 / 60.0,
        listOf(
            "Lina a un vélo. Elle roule dans la cour.",
            "Le chat dort sur le tapis. Il rêve d’une souris.",
            "Milo joue avec sa balle. Puis il rentre à la maison.",
        ),
    ),
    CE1(
        "CE1 — Cours élémentaire 1re année",
        "Cycle 2 · apprentissages fondamentaux",
        8,
        24.0 / 60.0,
        listOf(
            "Les petits lapins mangent des carottes dans le jardin.",
            "Ce matin, Zoé prépare son cartable et cherche ses crayons.",
            "La pluie tombe, mais les enfants jouent sous le préau.",
        ),
    ),
    CE2(
        "CE2 — Niveau intermédiaire",
        "Cycle 2 · apprentissages fondamentaux",
        10,
        34.0 / 60.0,
        listOf(
            "Hier, les élèves ont planté des graines près de l’école.",
            "Le vieux bateau avance lentement entre les rochers.",
            "Demain, nous visiterons le musée avec notre classe.",
        ),
    ),
    CM1(
        "CM1 — Niveau avancé",
        "Cycle 3 · consolidation",
        12,
        45.0 / 60.0,
        listOf(
            "Quand le vent se lève, les grandes branches bougent et les oiseaux s’envolent.",
            "L’année dernière, nous avons découvert un sentier qui longeait la rivière.",
            "Mes cousins sont partis tôt, mais ils ont oublié leurs gourdes.",
        ),
    ),
    CM2(
        "CM2 — Perfectionnement",
        "Cycle 3 · consolidation",
        14,
        46.0 / 60.0,
        listOf(
            "Après la pluie, les chemins glissants que nous avions suivis brillaient sous les éclaircies.",
            "Si tu prends le temps de relire tes phrases, tu repéreras les accords oubliés.",
            "Les exploratrices avaient préparé leurs sacs avant de partir vers les montagnes enneigées.",
        ),
    ),
}

data class Exercise(
    val id: String,
    val level: SchoolLevel,
    val sourceText: String,
    val fragments: List<String>,
)

private val wordPattern = Regex("\\S+")

fun countLetters(value: String): Int = value.count { it.isLetter() }

/** Splits at sentence punctuation first, then packs complete words under the letter limit. */
fun splitTextIntoFragments(text: String, maxLetters: Int): List<String> {
    require(maxLetters > 0) { "maxLetters must be positive" }
    val sentences = text.trim().split(Regex("(?<=[.!?])\\s+"))
    val fragments = mutableListOf<String>()
    val current = mutableListOf<String>()
    var currentLetters = 0

    fun flush() {
        if (current.isNotEmpty()) {
            fragments += current.joinToString(" ")
            current.clear()
            currentLetters = 0
        }
    }

    for (sentence in sentences) {
        for (word in wordPattern.findAll(sentence).map { it.value }) {
            val wordLetters = countLetters(word)
            if (current.isNotEmpty() && currentLetters + 1 + wordLetters > maxLetters) flush()
            val separator = if (current.isEmpty()) 0 else 1
            current += word
            currentLetters += separator + wordLetters
            if (countLetters(current.joinToString(" ")) >= maxLetters && word.lastOrNull()?.let { it in ".!?" } == true) flush()
        }
    }
    flush()
    return fragments.ifEmpty { listOf(text.trim()) }
}

fun createExercise(level: SchoolLevel, maxLetters: Int = level.recommendedLetters, index: Int = 0): Exercise {
    val safeIndex = ((index % level.texts.size) + level.texts.size) % level.texts.size
    val source = level.texts[safeIndex]
    return Exercise(
        id = "${level.name.lowercase()}-$safeIndex-$maxLetters",
        level = level,
        sourceText = source,
        fragments = splitTextIntoFragments(source, maxLetters),
    )
}

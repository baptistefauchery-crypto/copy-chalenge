package com.baptiste.dicta.beta.domain

enum class SchoolLevel(
    val label: String,
    val cycle: String,
    val recommendedLetters: Int,
    /** Kept for source compatibility with the optional OCR flow; stable scoring does not use it. */
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
        "CE2 — Cours élémentaire 2e année",
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
        "CM1 — Cours moyen 1re année",
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
        "CM2 — Cours moyen 2e année",
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

fun countLetters(value: String): Int = value.count(Char::isLetter)

private val sentenceEndPattern = Regex("""[.!?](?:[»"')\]]*)$""")

private fun endsSentence(word: String): Boolean = sentenceEndPattern.containsMatchIn(word)

/**
 * Matches the stable web splitter: normalize whitespace, add complete words
 * until the target is reached, and always stop at a sentence boundary first.
 * A long final word may therefore make a fragment exceed [maxLetters].
 */
fun splitTextIntoFragments(text: String, maxLetters: Int): List<String> {
    require(maxLetters > 0) { "maxLetters must be positive" }
    val normalized = text.trim().replace(Regex("\\s+"), " ")
    if (normalized.isEmpty()) return emptyList()

    val words = normalized.split(' ')
    val fragments = mutableListOf<String>()
    var offset = 0

    while (offset < words.size) {
        var letters = 0
        var size = 0
        while (offset + size < words.size) {
            val word = words[offset + size]
            letters += countLetters(word)
            size += 1
            if (endsSentence(word) || letters >= maxLetters) break
        }
        fragments += words.subList(offset, offset + size).joinToString(" ")
        offset += size
    }

    return fragments
}

fun createExercise(level: SchoolLevel, maxLetters: Int = level.recommendedLetters, index: Int = 0): Exercise {
    val safeIndex = ((index % level.texts.size) + level.texts.size) % level.texts.size
    val source = level.texts[safeIndex]
    return Exercise(
        id = "${level.name.lowercase()}-$safeIndex-$maxLetters",
        level = level,
        sourceText = source,
        fragments = splitTextIntoFragments(source, maxLetters = maxLetters),
    )
}

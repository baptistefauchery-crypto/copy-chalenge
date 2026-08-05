package com.baptiste.dicta.beta.domain

import java.util.Locale

enum class FragmentMode { LETTERS, VERSES }

data class DictationDefinition(
    val text: String,
    val fragmentMode: FragmentMode = FragmentMode.LETTERS,
    val preserveVerseBreaks: Boolean = false,
)

data class TextFragment(
    val text: String,
    /** UI metadata for a colored bar/cue. */
    val endsVerse: Boolean,
)

private fun prose(text: String) = DictationDefinition(text)

private fun poem(text: String, fragmentMode: FragmentMode) = DictationDefinition(
    text = text.trimIndent(),
    fragmentMode = fragmentMode,
    preserveVerseBreaks = true,
)

private val laCigaleEtLaFourmi = """
    La Cigale, ayant chanté
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
    Eh bien ! dansez maintenant. »
"""

private val leCorbeauEtLeRenard = """
    Maître Corbeau, sur un arbre perché,
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
    Jura, mais un peu tard, qu’on ne l’y prendrait plus.
"""

private val leLoupEtLAgneau = """
    La raison du plus fort est toujours la meilleure :
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
    Sans autre forme de procès.
"""

enum class SchoolLevel(
    val label: String,
    val cycle: String,
    val recommendedLetters: Int,
    val referenceLettersPerSecond: Double,
    val dictations: List<DictationDefinition>,
) {
    CP(
        "CP — Cours préparatoire",
        "Cycle 2 · apprentissages fondamentaux",
        6,
        10.0 / 60.0,
        listOf(
            prose("Lina a un vélo. Elle roule dans la cour."),
            prose("Le chat dort sur le tapis. Il rêve d’une souris."),
            prose("Milo joue avec sa balle. Puis il rentre à la maison."),
        ),
    ),
    CE1(
        "CE1 — Cours élémentaire 1re année",
        "Cycle 2 · apprentissages fondamentaux",
        8,
        24.0 / 60.0,
        listOf(
            prose("Les petits lapins mangent des carottes dans le jardin."),
            prose("Ce matin, Zoé prépare son cartable et cherche ses crayons."),
            prose("La pluie tombe, mais les enfants jouent sous le préau."),
        ),
    ),
    CE2(
        "Niveau intermédiaire",
        "Cycle 2 · apprentissages fondamentaux",
        10,
        34.0 / 60.0,
        listOf(
            prose("Hier, les élèves ont planté des graines près de l’école."),
            prose("Le vieux bateau avance lentement entre les rochers."),
            prose("Demain, nous visiterons le musée avec notre classe."),
        ),
    ),
    CM1(
        "Niveau avancé",
        "Cycle 3 · consolidation",
        12,
        45.0 / 60.0,
        listOf(
            poem(laCigaleEtLaFourmi, FragmentMode.LETTERS),
            prose("L’année dernière, nous avons découvert un sentier qui longeait la rivière."),
            prose("Mes cousins sont partis tôt, mais ils ont oublié leurs gourdes."),
        ),
    ),
    CM2(
        "Perfectionnement",
        "Cycle 3 · consolidation",
        14,
        46.0 / 60.0,
        listOf(
            poem(leCorbeauEtLeRenard, FragmentMode.VERSES),
            poem(leLoupEtLAgneau, FragmentMode.VERSES),
            prose("Les exploratrices avaient préparé leurs sacs avant de partir vers les montagnes enneigées."),
        ),
    );

    /** Compatibility view used by persistence and the current ViewModel. */
    val texts: List<String> get() = dictations.map(DictationDefinition::text)
}

data class Exercise(
    val id: String,
    val level: SchoolLevel,
    val sourceText: String,
    val fragments: List<String>,
    val fragmentDetails: List<TextFragment> = fragments.map { TextFragment(it, endsVerse = false) },
)

fun countLetters(value: String): Int = value.count(Char::isLetter)

private val sentenceEndPattern = Regex("""[.!?](?:[»"')\]]*)$""")
private val linkedDeterminers = setOf("le", "la", "l'", "l’", "un", "une", "des", "les", "au", "aux")

private fun endsSentence(word: String): Boolean = sentenceEndPattern.containsMatchIn(word)

private fun isLinkedDeterminer(word: String): Boolean = word
    .trim('«', '“', '"', '(', '[', ',', ';', ':', '!', '?', '.', '”', '»', ')', ']')
    .lowercase(Locale.FRENCH) in linkedDeterminers

private fun splitLineByLetters(line: String, maxLetters: Int): List<String> {
    val normalized = line.trim().replace(Regex("[\\t ]+"), " ")
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
            val reachedBoundary = endsSentence(word) || letters >= maxLetters
            if (reachedBoundary && (!isLinkedDeterminer(word) || offset + size >= words.size)) break
        }
        fragments += words.subList(offset, offset + size).joinToString(" ")
        offset += size
    }
    return fragments
}

fun splitTextIntoFragmentDetails(
    text: String,
    maxLetters: Int,
    mode: FragmentMode = FragmentMode.LETTERS,
    preserveVerseBreaks: Boolean = false,
): List<TextFragment> {
    require(maxLetters > 0) { "maxLetters must be positive" }

    if (mode == FragmentMode.VERSES) {
        return text.lines()
            .map { it.trim().replace(Regex("[\\t ]+"), " ") }
            .filter(String::isNotEmpty)
            .map { TextFragment(it, endsVerse = true) }
    }

    if (preserveVerseBreaks) {
        return text.lines().flatMap { line ->
            val fragments = splitLineByLetters(line, maxLetters)
            fragments.mapIndexed { index, fragment ->
                TextFragment(fragment, endsVerse = index == fragments.lastIndex)
            }
        }
    }

    val normalized = text.trim().replace(Regex("\\s+"), " ")
    return splitLineByLetters(normalized, maxLetters).map { TextFragment(it, endsVerse = false) }
}

fun splitTextIntoFragments(
    text: String,
    maxLetters: Int,
    mode: FragmentMode = FragmentMode.LETTERS,
    preserveVerseBreaks: Boolean = false,
): List<String> = splitTextIntoFragmentDetails(text, maxLetters, mode, preserveVerseBreaks).map(TextFragment::text)

fun createExercise(level: SchoolLevel, maxLetters: Int = level.recommendedLetters, index: Int = 0): Exercise {
    val safeIndex = ((index % level.dictations.size) + level.dictations.size) % level.dictations.size
    val definition = level.dictations[safeIndex]
    val details = splitTextIntoFragmentDetails(
        text = definition.text,
        maxLetters = maxLetters,
        mode = definition.fragmentMode,
        preserveVerseBreaks = definition.preserveVerseBreaks,
    )
    return Exercise(
        id = "${level.name.lowercase()}-$safeIndex-$maxLetters",
        level = level,
        sourceText = definition.text,
        fragments = details.map(TextFragment::text),
        fragmentDetails = details,
    )
}

package com.baptiste.dicta.beta.ocr

import android.content.Context
import java.text.Normalizer
import java.util.Locale

data class SpellingIssue(val word: String, val suggestions: List<String>)
data class SpellingResult(val text: String, val checkedWords: Int, val issues: List<SpellingIssue>)

/** Small offline dictionary adapter. The full .aff/.dic files are shipped in assets. */
class FrenchSpellChecker(private val context: Context) {
    @Volatile private var words: Set<String>? = null

    fun check(text: String, ignoredText: String = ""): SpellingResult {
        val dictionary = loadWords()
        val ignored = wordPattern.findAll(ignoredText).map { key(it.value) }.toSet()
        val tokens = wordPattern.findAll(text).map { it.value }.toList()
        val issues = tokens.filter { key(it) !in ignored && !isKnown(key(it), dictionary) }
            .map { word -> SpellingIssue(word, suggestions(key(word), dictionary)) }
        return SpellingResult(text, tokens.size, issues)
    }

    private fun loadWords(): Set<String> {
        words?.let { return it }
        val loaded = runCatching {
            context.assets.open("index.dic").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).map { it.substringBefore('/').trim() }.filter { it.isNotEmpty() }
                    .map(::key).toSet()
            }
        }.getOrDefault(emptySet())
        words = loaded
        return loaded
    }

    private fun isKnown(word: String, dictionary: Set<String>): Boolean {
        if (word.length <= 1 || word.all { it.isDigit() }) return true
        if (word in dictionary) return true
        val parts = word.split('\'', '’')
        return parts.all { it in dictionary || it.length <= 1 }
    }

    private fun suggestions(word: String, dictionary: Set<String>): List<String> = dictionary.asSequence()
        .filter { kotlin.math.abs(it.length - word.length) <= 2 }
        .map { it to distance(word, it) }
        .sortedBy { it.second }
        .take(3)
        .map { it.first }
        .toList()

    private fun distance(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, char ->
            var previous = row[0]
            row[0] = i + 1
            b.forEachIndexed { j, other ->
                val next = row[j + 1]
                row[j + 1] = minOf(row[j + 1] + 1, row[j] + 1, previous + if (char == other) 0 else 1)
                previous = next
            }
        }
        return row.last()
    }

    private fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .lowercase(Locale.FRENCH)

    private companion object {
        val wordPattern = Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")
    }
}

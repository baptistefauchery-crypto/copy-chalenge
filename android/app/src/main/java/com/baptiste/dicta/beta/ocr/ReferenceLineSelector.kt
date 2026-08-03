package com.baptiste.dicta.beta.ocr

import kotlin.math.max

enum class OcrSelectionStatus { FOUND, REFERENCE_EMPTY, NOT_FOUND }

data class OcrLineSelection(
    val status: OcrSelectionStatus,
    val result: OcrResult,
    val startLineIndex: Int?,
    val endLineIndexExclusive: Int?,
    /** Similarity between the first expected words and the selected OCR lines. */
    val anchorConfidence: Double,
    val detectedLineCount: Int,
)

/**
 * Finds the first OCR line belonging to [reference] and the most likely last
 * line. Previous exercises above it and unrelated writing below it are not
 * returned. A weak anchor fails closed so the UI can ask for a better photo
 * instead of grading the entire sheet.
 */
fun selectReferenceLines(
    reference: String,
    lines: List<OcrToken>,
    minimumAnchorConfidence: Double = 0.42,
): OcrLineSelection {
    val readableLines = lines.filter { normalizeOcr(it.text).isNotEmpty() }
    val normalizedReference = normalizeOcr(reference)
    if (normalizedReference.isEmpty()) {
        return selection(
            status = OcrSelectionStatus.REFERENCE_EMPTY,
            lines = emptyList(),
            detectedLineCount = readableLines.size,
        )
    }
    if (readableLines.isEmpty()) {
        return selection(
            status = OcrSelectionStatus.NOT_FOUND,
            lines = emptyList(),
            detectedLineCount = 0,
        )
    }

    val referenceWords = words(normalizedReference)
    val prefix = referenceWords.take(MAX_ANCHOR_WORDS)
    val starts = readableLines.indices.map { start ->
        val candidateWords = words(readableLines.drop(start).joinToString(" ") { it.text }).take(prefix.size)
        val lexicalSimilarity = sequenceSimilarity(prefix, candidateWords)
        val ocrConfidence = weightedConfidence(readableLines.drop(start).take(linesNeededForWords(readableLines, start, prefix.size)))
        StartCandidate(start, lexicalSimilarity * 0.9 + ocrConfidence * 0.1)
    }
    val bestStart = starts.maxWithOrNull(compareBy<StartCandidate> { it.anchorConfidence }.thenBy { -it.index })!!
    if (bestStart.anchorConfidence < minimumAnchorConfidence) {
        return selection(
            status = OcrSelectionStatus.NOT_FOUND,
            lines = emptyList(),
            detectedLineCount = readableLines.size,
            anchorConfidence = bestStart.anchorConfidence,
        )
    }

    val candidateRanges = (bestStart.index + 1..readableLines.size).map { endExclusive ->
        val candidate = readableLines.subList(bestStart.index, endExclusive)
        val candidateText = normalizeOcr(candidate.joinToString(" ") { it.text })
        val similarity = characterSimilarity(normalizedReference, candidateText)
        val lengthBalance = if (normalizedReference.isEmpty() && candidateText.isEmpty()) 1.0 else {
            minOf(normalizedReference.length, candidateText.length).toDouble() /
                max(1, max(normalizedReference.length, candidateText.length))
        }
        val score = similarity * 0.72 + lengthBalance * 0.2 + weightedConfidence(candidate) * 0.08
        RangeCandidate(endExclusive, score)
    }
    val bestRange = candidateRanges.maxWithOrNull(compareBy<RangeCandidate> { it.score }.thenBy { -it.endExclusive })!!
    val selected = readableLines.subList(bestStart.index, bestRange.endExclusive)
    return selection(
        status = OcrSelectionStatus.FOUND,
        lines = selected,
        detectedLineCount = readableLines.size,
        startLineIndex = bestStart.index,
        endLineIndexExclusive = bestRange.endExclusive,
        anchorConfidence = bestStart.anchorConfidence,
    )
}

private fun selection(
    status: OcrSelectionStatus,
    lines: List<OcrToken>,
    detectedLineCount: Int,
    startLineIndex: Int? = null,
    endLineIndexExclusive: Int? = null,
    anchorConfidence: Double = 0.0,
): OcrLineSelection = OcrLineSelection(
    status = status,
    result = OcrResult(
        text = lines.joinToString(" ") { it.text }.trim(),
        confidence = weightedConfidence(lines),
        tokens = lines,
    ),
    startLineIndex = startLineIndex,
    endLineIndexExclusive = endLineIndexExclusive,
    anchorConfidence = anchorConfidence.coerceIn(0.0, 1.0),
    detectedLineCount = detectedLineCount,
)

private fun linesNeededForWords(lines: List<OcrToken>, start: Int, wantedWords: Int): Int {
    var count = 0
    for (index in start until lines.size) {
        count += words(lines[index].text).size
        if (count >= wantedWords) return index - start + 1
    }
    return lines.size - start
}

private fun words(value: String): List<String> = WORD_PATTERN.findAll(normalizeOcr(value)).map { it.value }.toList()

private fun weightedConfidence(lines: List<OcrToken>): Double {
    if (lines.isEmpty()) return 0.0
    val weights = lines.map { token -> token.text.count { it.isLetterOrDigit() }.coerceAtLeast(1) }
    val totalWeight = weights.sum().coerceAtLeast(1)
    return lines.zip(weights).sumOf { (line, weight) -> line.confidence.coerceIn(0.0, 1.0) * weight } / totalWeight
}

private fun sequenceSimilarity(expected: List<String>, actual: List<String>): Double {
    if (expected.isEmpty()) return if (actual.isEmpty()) 1.0 else 0.0
    return 1.0 - editDistance(expected, actual).toDouble() / max(expected.size, actual.size).coerceAtLeast(1)
}

private fun characterSimilarity(expected: String, actual: String): Double {
    if (expected.isEmpty()) return if (actual.isEmpty()) 1.0 else 0.0
    return 1.0 - editDistance(expected.toList(), actual.toList()).toDouble() /
        max(expected.length, actual.length).coerceAtLeast(1)
}

private fun <T> editDistance(expected: List<T>, actual: List<T>): Int {
    var previous = IntArray(actual.size + 1) { it }
    expected.forEachIndexed { row, expectedItem ->
        val current = IntArray(actual.size + 1)
        current[0] = row + 1
        actual.forEachIndexed { column, actualItem ->
            current[column + 1] = minOf(
                previous[column + 1] + 1,
                current[column] + 1,
                previous[column] + if (expectedItem == actualItem) 0 else 1,
            )
        }
        previous = current
    }
    return previous.last()
}

private data class StartCandidate(val index: Int, val anchorConfidence: Double)
private data class RangeCandidate(val endExclusive: Int, val score: Double)

private const val MAX_ANCHOR_WORDS = 5
private val WORD_PATTERN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['\\u2019][\\p{L}\\p{M}\\p{N}]+)*")

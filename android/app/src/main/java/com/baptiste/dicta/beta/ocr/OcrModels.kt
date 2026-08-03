package com.baptiste.dicta.beta.ocr

data class OcrToken(val text: String, val confidence: Double)

data class OcrResult(
    val text: String,
    val confidence: Double,
    val tokens: List<OcrToken> = emptyList(),
)

enum class ComparisonStatus { MATCH, PROBABLE, UNCERTAIN }

enum class DifferenceKind { INSERT, DELETE, REPLACE }

data class WordDifference(
    val reference: String,
    val recognized: String,
    val status: ComparisonStatus,
    val kind: DifferenceKind = DifferenceKind.REPLACE,
)

data class OcrComparison(
    val reference: String,
    val recognized: String,
    val confidence: Double,
    val status: ComparisonStatus,
    val matches: Boolean,
    val differences: List<WordDifference>,
)

data class OcrScanAnalysis(
    val modelName: String,
    val selection: OcrLineSelection,
    val comparison: OcrComparison,
    val processingMs: Long,
)

private val wordPattern = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*")

fun normalizeOcr(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
    .replace('’', '\'')
    .replace(Regex("\\s+"), " ")
    .trim()
    .lowercase(java.util.Locale.FRENCH)

fun compareOcrToReference(reference: String, result: OcrResult): OcrComparison {
    val normalizedReference = normalizeOcr(reference)
    val normalizedRecognized = normalizeOcr(result.text)
    val referenceWords = wordPattern.findAll(normalizedReference).map { it.value }.toList()
    val recognizedWords = wordPattern.findAll(normalizedRecognized).map { it.value }.toList()
    val differences = alignWordDifferences(referenceWords, recognizedWords, result.confidence)
    val exact = normalizedReference == normalizedRecognized && normalizedReference.isNotEmpty()
    return OcrComparison(
        reference = reference,
        recognized = result.text,
        confidence = result.confidence.coerceIn(0.0, 1.0),
        status = if (exact) statusForExact(result.confidence) else statusFor(result.confidence),
        matches = exact,
        differences = differences,
    )
}

private fun alignWordDifferences(
    reference: List<String>,
    recognized: List<String>,
    confidence: Double,
): List<WordDifference> {
    val costs = Array(reference.size + 1) { row -> IntArray(recognized.size + 1) { column -> row + column } }
    for (row in 1..reference.size) {
        for (column in 1..recognized.size) {
            costs[row][column] = minOf(
                costs[row - 1][column] + 1,
                costs[row][column - 1] + 1,
                costs[row - 1][column - 1] + if (reference[row - 1] == recognized[column - 1]) 0 else 1,
            )
        }
    }

    val differences = mutableListOf<WordDifference>()
    var row = reference.size
    var column = recognized.size
    while (row > 0 || column > 0) {
        if (row > 0 && column > 0 && reference[row - 1] == recognized[column - 1]) {
            row--
            column--
            continue
        }
        val replacementCost = if (row > 0 && column > 0) costs[row - 1][column - 1] else Int.MAX_VALUE
        val deletionCost = if (row > 0) costs[row - 1][column] else Int.MAX_VALUE
        val insertionCost = if (column > 0) costs[row][column - 1] else Int.MAX_VALUE
        when (minOf(replacementCost, deletionCost, insertionCost)) {
            replacementCost -> {
                differences += WordDifference(
                    reference = reference[row - 1],
                    recognized = recognized[column - 1],
                    status = statusFor(confidence),
                    kind = DifferenceKind.REPLACE,
                )
                row--
                column--
            }
            deletionCost -> {
                differences += WordDifference(
                    reference = reference[row - 1],
                    recognized = "",
                    status = statusFor(confidence),
                    kind = DifferenceKind.DELETE,
                )
                row--
            }
            else -> {
                differences += WordDifference(
                    reference = "",
                    recognized = recognized[column - 1],
                    status = statusFor(confidence),
                    kind = DifferenceKind.INSERT,
                )
                column--
            }
        }
    }
    return differences.asReversed()
}

private fun statusFor(confidence: Double) = if (confidence >= 0.8) ComparisonStatus.PROBABLE else ComparisonStatus.UNCERTAIN
private fun statusForExact(confidence: Double) = if (confidence >= 0.55) ComparisonStatus.MATCH else ComparisonStatus.UNCERTAIN

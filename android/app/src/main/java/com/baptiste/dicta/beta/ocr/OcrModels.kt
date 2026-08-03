package com.baptiste.dicta.beta.ocr

data class OcrToken(val text: String, val confidence: Double)

data class OcrResult(
    val text: String,
    val confidence: Double,
    val tokens: List<OcrToken> = emptyList(),
)

enum class ComparisonStatus { MATCH, PROBABLE, UNCERTAIN }

data class WordDifference(
    val reference: String,
    val recognized: String,
    val status: ComparisonStatus,
)

data class OcrComparison(
    val reference: String,
    val recognized: String,
    val confidence: Double,
    val status: ComparisonStatus,
    val matches: Boolean,
    val differences: List<WordDifference>,
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
    val differences = mutableListOf<WordDifference>()
    val size = maxOf(referenceWords.size, recognizedWords.size)
    repeat(size) { index ->
        val expected = referenceWords.getOrNull(index).orEmpty()
        val actual = recognizedWords.getOrNull(index).orEmpty()
        if (expected != actual) {
            differences += WordDifference(expected, actual, statusFor(result.confidence))
        }
    }
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

private fun statusFor(confidence: Double) = if (confidence >= 0.8) ComparisonStatus.PROBABLE else ComparisonStatus.UNCERTAIN
private fun statusForExact(confidence: Double) = if (confidence >= 0.55) ComparisonStatus.MATCH else ComparisonStatus.UNCERTAIN

package com.baptiste.dicta.beta.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrReferenceSelectionTest {
    @Test
    fun recognitionTensorUsesMinusOneToOneInsteadOfImageNetNormalization() {
        assertEquals(
            -1.0,
            normalizeOcrTensorComponent(0, 0, OcrTensorPreprocessing.RECOGNITION).toDouble(),
            0.0001,
        )
        assertEquals(
            1.0,
            normalizeOcrTensorComponent(255, 2, OcrTensorPreprocessing.RECOGNITION).toDouble(),
            0.0001,
        )
        assertEquals(
            (1.0 - 0.485) / 0.229,
            normalizeOcrTensorComponent(255, 0, OcrTensorPreprocessing.DETECTION).toDouble(),
            0.0001,
        )
        assertTrue(normalizeOcrTensorComponent(128, 1, OcrTensorPreprocessing.RECOGNITION) in 0f..0.01f)
    }

    @Test
    fun selectsDictationFromItsOpeningWordsAndDropsPreviousExercise() {
        val selection = selectReferenceLines(
            reference = "La Cigale, ayant chanté tout l'été, se trouva fort dépourvue.",
            lines = listOf(
                OcrToken("Ancien exercice de calcul", 0.97),
                OcrToken("Le résultat est vingt-quatre", 0.95),
                OcrToken("La Cigale ayant chanté tout l'été", 0.91),
                OcrToken("se trouva fort dépourvue", 0.88),
                OcrToken("Signature des parents", 0.99),
            ),
        )

        assertEquals(OcrSelectionStatus.FOUND, selection.status)
        assertEquals(2, selection.startLineIndex)
        assertEquals(4, selection.endLineIndexExclusive)
        assertEquals("La Cigale ayant chanté tout l'été se trouva fort dépourvue", selection.result.text)
        assertEquals(2, selection.result.tokens.size)
        assertTrue(selection.anchorConfidence > 0.8)
    }

    @Test
    fun toleratesAnOcrErrorInTheOpeningAnchor() {
        val selection = selectReferenceLines(
            reference = "Le Corbeau, sur un arbre perché, tenait en son bec un fromage.",
            lines = listOf(
                OcrToken("Une autre phrase", 0.94),
                OcrToken("Le Corbean sur un arbre perché", 0.72),
                OcrToken("tenait en son bec un fromage", 0.81),
            ),
        )

        assertEquals(OcrSelectionStatus.FOUND, selection.status)
        assertEquals(1, selection.startLineIndex)
        assertEquals(3, selection.endLineIndexExclusive)
    }

    @Test
    fun failsClosedWhenTheBeginningOfTheDictationIsNotVisible() {
        val selection = selectReferenceLines(
            reference = "Le Loup survient à jeun qui cherchait aventure.",
            lines = listOf(
                OcrToken("Table de multiplication", 0.99),
                OcrToken("Signature des parents", 0.99),
            ),
        )

        assertEquals(OcrSelectionStatus.NOT_FOUND, selection.status)
        assertEquals("", selection.result.text)
        assertEquals(null, selection.startLineIndex)
    }

    @Test
    fun confidenceIsWeightedByRecognizedCharacterCount() {
        val selection = selectReferenceLines(
            reference = "La longue phrase continue x.",
            lines = listOf(
                OcrToken("La longue phrase continue", 0.9),
                OcrToken("x", 0.1),
            ),
        )

        assertEquals(OcrSelectionStatus.FOUND, selection.status)
        assertTrue(selection.result.confidence > 0.8)
        assertTrue(selection.result.confidence < 0.9)
    }

    @Test
    fun wordAlignmentDoesNotTurnOneOmissionIntoManyReplacements() {
        val comparison = compareOcrToReference(
            "Le petit chat noir dort",
            OcrResult("Le petit noir dort", 0.93),
        )

        assertFalse(comparison.matches)
        assertEquals(1, comparison.differences.size)
        assertEquals(DifferenceKind.DELETE, comparison.differences.single().kind)
        assertEquals("chat", comparison.differences.single().reference)
        assertEquals("", comparison.differences.single().recognized)
    }

    @Test
    fun emptyReferenceIsReportedWithoutSelectingThePage() {
        val selection = selectReferenceLines(
            reference = "   ",
            lines = listOf(OcrToken("Ancien exercice", 0.9)),
        )

        assertEquals(OcrSelectionStatus.REFERENCE_EMPTY, selection.status)
        assertEquals("", selection.result.text)
        assertEquals(1, selection.detectedLineCount)
    }
}

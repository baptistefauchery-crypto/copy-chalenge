package com.baptiste.dicta.beta.domain

import kotlin.math.pow
import kotlin.math.roundToInt

/** Compatibility-only OCR inputs; the stable score intentionally ignores them. */
data class ScoreOptions(
    val spellingFaults: Int,
    val wordCount: Int,
    val ocrConfidence: Double,
    val speedReferenceLettersPerSecond: Double,
)

data class ScoreReward(val stars: Int, val badge: Badge)
enum class Badge { NONE, BRONZE, SILVER, GOLD, TROPHY }

const val MAX_SCORE = 100
const val SCORE_POINTS_PER_LETTER = 80
const val REVIEW_SCORE_MULTIPLIER = 0.8

fun countScoringLetters(text: String): Int = text.count(Char::isLetter)

/** Retained for the optional OCR comparison UI; it is not part of stable scoring. */
fun countScoringWords(text: String): Int =
    Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*").findAll(text).count()

/** Stable v52 formula: round(letters * 80 * 0.8^reviews / elapsedSeconds), clamped to 0..100. */
fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int = 0): Int {
    val letters = countScoringLetters(text)
    if (letters == 0) return 0

    val elapsedSeconds = maxOf(1.0, elapsedMs / 1000.0)
    val reviews = maxOf(0, reviewCount)
    val rawScore = letters * SCORE_POINTS_PER_LETTER * REVIEW_SCORE_MULTIPLIER.pow(reviews) / elapsedSeconds
    return rawScore.roundToInt().coerceIn(0, MAX_SCORE)
}

/** Keeps the current OCR-aware ViewModel source-compatible while applying the stable formula. */
@Suppress("UNUSED_PARAMETER")
fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int, options: ScoreOptions): Int =
    calculateScore(text, elapsedMs, reviewCount)

fun rewardFor(score: Int): ScoreReward {
    val normalized = score.coerceIn(0, MAX_SCORE)
    return ScoreReward(
        stars = when {
            normalized >= 60 -> 3
            normalized >= 40 -> 2
            normalized >= 20 -> 1
            else -> 0
        },
        badge = when {
            normalized >= 100 -> Badge.TROPHY
            normalized >= 90 -> Badge.GOLD
            normalized >= 80 -> Badge.SILVER
            normalized >= 70 -> Badge.BRONZE
            else -> Badge.NONE
        },
    )
}

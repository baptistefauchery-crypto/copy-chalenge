package com.baptiste.dicta.beta.domain

import kotlin.math.pow
import kotlin.math.roundToInt

data class ScoreOptions(
    val speedReferenceLettersPerSecond: Double,
)

data class ScoreBreakdown(
    val score: Int,
    val rawScore: Double,
    val baseScore: Double,
    val speedAdjustment: Double,
    val lengthBonus: Double,
    val reviewMultiplier: Double,
)

data class ScoreReward(val stars: Int, val badge: Badge)
enum class Badge { NONE, BRONZE, SILVER, GOLD, TROPHY }

const val MAX_SCORE = 100
const val SCORE_BASE = 100.0
const val MAX_SPEED_ADJUSTMENT = 6.0
const val MAX_LENGTH_BONUS = 5.0
const val MIN_LENGTH_FOR_BONUS = 50
const val LENGTH_FOR_MAX_BONUS = 250
const val EXPECTED_SPEED_RATIO = 0.75
const val DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND = 2.0
const val REVIEW_SCORE_MULTIPLIER = 0.8
private const val MIN_SPEED_REFERENCE = 2.220446049250313e-16

fun countScoringLetters(text: String): Int = text.count(Char::isLetter)

fun calculateScore(
    text: String,
    elapsedMs: Long,
    reviewCount: Int = 0,
    speedReferenceLettersPerSecond: Double = DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND,
): Int = calculateScoreBreakdown(
    text,
    elapsedMs,
    reviewCount,
    ScoreOptions(speedReferenceLettersPerSecond),
).score

fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int, options: ScoreOptions): Int =
    calculateScoreBreakdown(text, elapsedMs, reviewCount, options).score

/** Shared score contract: time, length, and the number of reviews only. */
fun calculateScoreBreakdown(
    text: String,
    elapsedMs: Long,
    reviewCount: Int = 0,
    options: ScoreOptions,
): ScoreBreakdown {
    val letters = countScoringLetters(text)
    if (letters == 0) {
        return ScoreBreakdown(0, 0.0, SCORE_BASE, 0.0, 0.0, 1.0)
    }

    val elapsedSeconds = maxOf(1.0, elapsedMs / 1000.0)
    val reviews = maxOf(0, reviewCount)
    val speedReference = if (options.speedReferenceLettersPerSecond.isFinite()) {
        maxOf(options.speedReferenceLettersPerSecond, MIN_SPEED_REFERENCE)
    } else {
        DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND
    }
    val lettersPerSecond = letters / elapsedSeconds
    val speedRatio = lettersPerSecond / speedReference
    val normalizedSpeed = clampSigned((speedRatio - EXPECTED_SPEED_RATIO) / EXPECTED_SPEED_RATIO)
    val speedAdjustment = MAX_SPEED_ADJUSTMENT * normalizedSpeed
    val lengthProgress = (letters - MIN_LENGTH_FOR_BONUS).toDouble() /
        (LENGTH_FOR_MAX_BONUS - MIN_LENGTH_FOR_BONUS)
    val lengthBonus = MAX_LENGTH_BONUS * clampUnit(lengthProgress)
    val reviewMultiplier = REVIEW_SCORE_MULTIPLIER.pow(reviews)
    val rawScore = maxOf(0.0, SCORE_BASE + speedAdjustment + lengthBonus) * reviewMultiplier

    return ScoreBreakdown(
        score = rawScore.roundToInt().coerceIn(0, MAX_SCORE),
        rawScore = rawScore,
        baseScore = SCORE_BASE,
        speedAdjustment = speedAdjustment,
        lengthBonus = lengthBonus,
        reviewMultiplier = reviewMultiplier,
    )
}

private fun clampUnit(value: Double): Double =
    if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0

private fun clampSigned(value: Double): Double =
    if (value.isFinite()) value.coerceIn(-1.0, 1.0) else -1.0

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

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
const val SCORE_POINTS_PER_LETTER = 80.0
const val DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND = 2.0
const val REVIEW_SCORE_MULTIPLIER = 0.8

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

/** Shared score contract: copied letters per second and rereads only. */
fun calculateScoreBreakdown(
    text: String,
    elapsedMs: Long,
    reviewCount: Int = 0,
    @Suppress("UNUSED_PARAMETER") options: ScoreOptions,
): ScoreBreakdown {
    val letters = countScoringLetters(text)
    if (letters == 0) {
        return ScoreBreakdown(0, 0.0, 0.0, 0.0, 0.0, 1.0)
    }

    val elapsedSeconds = maxOf(1.0, elapsedMs / 1000.0)
    val reviews = maxOf(0, reviewCount)
    val reviewMultiplier = REVIEW_SCORE_MULTIPLIER.pow(reviews)
    val baseScore = letters * SCORE_POINTS_PER_LETTER / elapsedSeconds
    val rawScore = baseScore * reviewMultiplier

    return ScoreBreakdown(
        score = rawScore.roundToInt().coerceIn(0, MAX_SCORE),
        rawScore = rawScore,
        baseScore = baseScore,
        speedAdjustment = 0.0,
        lengthBonus = 0.0,
        reviewMultiplier = reviewMultiplier,
    )
}

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

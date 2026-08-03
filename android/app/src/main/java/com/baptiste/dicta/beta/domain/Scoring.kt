package com.baptiste.dicta.beta.domain

import kotlin.math.pow
import kotlin.math.roundToInt

data class ScoreOptions(
    val spellingFaults: Int,
    val wordCount: Int,
    val ocrConfidence: Double,
    val speedReferenceLettersPerSecond: Double,
)

data class ScoreBreakdown(
    val score: Int,
    val rawScore: Double,
    val baseScore: Double,
    val faultPenalty: Double,
    val speedAdjustment: Double,
    val lengthBonus: Double,
    val confidenceAdjustment: Double,
    val reviewMultiplier: Double,
)

data class ScoreReward(val stars: Int, val badge: Badge)
enum class Badge { NONE, BRONZE, SILVER, GOLD, TROPHY }

const val MAX_SCORE = 100
const val SCORE_BASE = 100.0
const val MAX_FAULT_PENALTY = 45.0
const val MAX_SPEED_ADJUSTMENT = 6.0
const val MAX_LENGTH_BONUS = 5.0
const val MIN_LENGTH_FOR_BONUS = 50
const val LENGTH_FOR_MAX_BONUS = 250
const val EXPECTED_SPEED_RATIO = 0.75
const val EXPECTED_OCR_CONFIDENCE = 0.75
const val OCR_CONFIDENCE_SCALE = 4.0
const val DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND = 2.0
const val REVIEW_SCORE_MULTIPLIER = 0.8
private const val MIN_SPEED_REFERENCE = 2.220446049250313e-16

fun countScoringLetters(text: String): Int = text.count(Char::isLetter)

fun countScoringWords(text: String): Int =
    Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*").findAll(text).count()

/**
 * Compatibility overload for callers which do not have OCR results yet.
 * The complete post-scan score must call the overload accepting [ScoreOptions].
 */
fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int = 0): Int {
    val options = ScoreOptions(
        spellingFaults = 0,
        wordCount = maxOf(1, countScoringWords(text)),
        ocrConfidence = EXPECTED_OCR_CONFIDENCE,
        speedReferenceLettersPerSecond = DEFAULT_SPEED_REFERENCE_LETTERS_PER_SECOND,
    )
    return calculateScore(text, elapsedMs, reviewCount, options)
}

fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int, options: ScoreOptions): Int =
    calculateScoreBreakdown(text, elapsedMs, reviewCount, options).score

/**
 * Shared Android/Web scoring contract.
 *
 * The neutral target is 100 points with no fault, 75% of reference speed and
 * 75% OCR confidence, so perfect speed/readability are not required. Reviews
 * have a 20% compounding penalty, faults remove up to 45 points, speed changes
 * at most +/-6 points, length adds at most 5, and OCR changes -3 to +1.
 * A flawless long and fast dictation can therefore reach 112 raw points before
 * the displayed [score] is rounded and capped to [MAX_SCORE].
 */
fun calculateScoreBreakdown(
    text: String,
    elapsedMs: Long,
    reviewCount: Int = 0,
    options: ScoreOptions,
): ScoreBreakdown {
    val letters = countScoringLetters(text)
    if (letters == 0) {
        return ScoreBreakdown(
            score = 0,
            rawScore = 0.0,
            baseScore = SCORE_BASE,
            faultPenalty = 0.0,
            speedAdjustment = 0.0,
            lengthBonus = 0.0,
            confidenceAdjustment = 0.0,
            reviewMultiplier = 1.0,
        )
    }

    val elapsedSeconds = maxOf(1.0, elapsedMs / 1000.0)
    val reviews = maxOf(0, reviewCount)
    val wordCount = maxOf(1, options.wordCount)
    val spellingFaults = maxOf(0, options.spellingFaults)
    val faultRate = clampUnit(spellingFaults.toDouble() / wordCount)
    val faultPenalty = MAX_FAULT_PENALTY * faultRate
    val confidence = clampUnit(options.ocrConfidence)
    val confidenceAdjustment = OCR_CONFIDENCE_SCALE * (confidence - EXPECTED_OCR_CONFIDENCE)
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
    val rawScore = maxOf(
        0.0,
        SCORE_BASE - faultPenalty + speedAdjustment + lengthBonus + confidenceAdjustment,
    ) * reviewMultiplier

    return ScoreBreakdown(
        score = rawScore.roundToInt().coerceIn(0, MAX_SCORE),
        rawScore = rawScore,
        baseScore = SCORE_BASE,
        faultPenalty = faultPenalty,
        speedAdjustment = speedAdjustment,
        lengthBonus = lengthBonus,
        confidenceAdjustment = confidenceAdjustment,
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

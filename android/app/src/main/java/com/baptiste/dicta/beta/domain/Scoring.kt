package com.baptiste.dicta.beta.domain

data class ScoreOptions(
    val spellingFaults: Int,
    val wordCount: Int,
    val ocrConfidence: Double,
    val speedReferenceLettersPerSecond: Double,
)

data class ScoreReward(val stars: Int, val badge: Badge)
enum class Badge { NONE, BRONZE, SILVER, GOLD, TROPHY }

fun countScoringLetters(text: String): Int = text.count { it.isLetter() }
fun countScoringWords(text: String): Int = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*").findAll(text).count()

fun calculateScore(text: String, elapsedMs: Long, reviewCount: Int, options: ScoreOptions): Int {
    val letters = countScoringLetters(text)
    if (letters == 0) return 0
    val seconds = maxOf(1.0, elapsedMs.coerceAtLeast(0L) / 1000.0)
    val words = maxOf(1, options.wordCount)
    val faults = maxOf(0, options.spellingFaults)
    val spellingScore = maxOf(0.0, 1.0 - 2.0 * faults / words)
    val confidence = options.ocrConfidence.coerceIn(0.0, 1.0)
    val speedReference = maxOf(options.speedReferenceLettersPerSecond, Double.MIN_VALUE)
    val speedScore = minOf(1.0, (letters / seconds) / speedReference)
    val reviewMultiplier = Math.pow(0.8, maxOf(0, reviewCount).toDouble())
    val raw = 110.0 * (0.6 * spellingScore + 0.2 * confidence + 0.2 * speedScore) * reviewMultiplier
    return raw.roundToInt().coerceIn(0, 100)
}

fun rewardFor(score: Int): ScoreReward {
    val normalized = score.coerceIn(0, 100)
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

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

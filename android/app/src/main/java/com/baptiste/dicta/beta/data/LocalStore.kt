package com.baptiste.dicta.beta.data

import android.content.Context
import com.baptiste.dicta.beta.domain.MAX_SCORE
import com.baptiste.dicta.beta.domain.SchoolLevel

private const val PREFERENCES_NAME = "dicta-beta"
private const val PROGRESS_PREFIX = "copy-challenge-dictation-progress-v1"
private const val LEADERBOARD_KEY = "copy-challenge-leaderboard-v1"
private const val LEGACY_LEADERBOARD_KEY = "leaderboard"
private const val LEADERBOARD_LIMIT = 5

val INITIAL_CURSORS: Map<SchoolLevel, Int> = mapOf(
    SchoolLevel.CP to 1,
    SchoolLevel.CE1 to 0,
    SchoolLevel.CE2 to 0,
    SchoolLevel.CM1 to 0,
    SchoolLevel.CM2 to 0,
)

data class StoredProgress(
    val level: SchoolLevel,
    val index: Int,
    val cursors: Map<SchoolLevel, Int>,
    val lettersPerFragment: Int,
)

data class LeaderboardEntry(
    val id: String,
    val score: Int,
    val createdAt: Long,
)

fun defaultProgress(level: SchoolLevel = SchoolLevel.CP): StoredProgress = StoredProgress(
    level = level,
    index = 0,
    cursors = INITIAL_CURSORS,
    lettersPerFragment = level.recommendedLetters,
)

fun normalizeProgress(progress: StoredProgress): StoredProgress {
    val cursors = SchoolLevel.values().associateWith { level ->
        progress.cursors[level]?.takeIf { it >= 0 } ?: INITIAL_CURSORS.getValue(level)
    }
    return progress.copy(
        cursors = cursors,
        lettersPerFragment = progress.lettersPerFragment.coerceIn(1, 100),
    )
}

fun advanceChallengeProgress(progress: StoredProgress): StoredProgress {
    val level = progress.level
    val nextIndex = ((progress.index + 1) % level.texts.size + level.texts.size) % level.texts.size
    val cursors = progress.cursors.toMutableMap().apply {
        this[level] = (nextIndex + 1) % level.texts.size
    }
    return progress.copy(
        index = nextIndex,
        cursors = cursors,
        lettersPerFragment = level.recommendedLetters,
    )
}

fun sortLeaderboard(entries: Iterable<LeaderboardEntry>): List<LeaderboardEntry> = entries
    .filter { it.id.isNotBlank() && it.score in 0..MAX_SCORE && it.createdAt >= 0L }
    .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.createdAt })
    .take(LEADERBOARD_LIMIT)

fun encodeLeaderboard(entries: Iterable<LeaderboardEntry>): String = sortLeaderboard(entries)
    .joinToString(";") { "${it.id}|${it.score}|${it.createdAt}" }

fun decodeLeaderboard(value: String): List<LeaderboardEntry> = sortLeaderboard(
    value.split(';').mapIndexedNotNull { index, row ->
        if (row.isBlank()) return@mapIndexedNotNull null
        val parts = row.split('|')
        when (parts.size) {
            3 -> runCatching {
                LeaderboardEntry(parts[0], parts[1].toInt(), parts[2].toLong())
            }.getOrNull()
            // Migration from the first Android beta, which stored only score and date.
            2 -> runCatching {
                val score = parts[0].toInt()
                val createdAt = parts[1].toLong()
                LeaderboardEntry("legacy-$createdAt-$score-$index", score, createdAt)
            }.getOrNull()
            else -> null
        }
    },
)

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readProgress(): StoredProgress {
        val level = preferences.getString("$PROGRESS_PREFIX.level", null)
            ?.let { stored -> SchoolLevel.values().firstOrNull { it.name == stored } }
            ?: SchoolLevel.CP
        val defaults = defaultProgress(level)
        val cursors = SchoolLevel.values().associateWith { cursorLevel ->
            preferences.getInt(
                "$PROGRESS_PREFIX.cursor.${cursorLevel.name}",
                defaults.cursors.getValue(cursorLevel),
            ).takeIf { it >= 0 } ?: defaults.cursors.getValue(cursorLevel)
        }
        return normalizeProgress(
            StoredProgress(
                level = level,
                index = preferences.getInt("$PROGRESS_PREFIX.index", defaults.index),
                cursors = cursors,
                lettersPerFragment = preferences.getInt(
                    "$PROGRESS_PREFIX.lettersPerFragment",
                    defaults.lettersPerFragment,
                ),
            ),
        )
    }

    fun saveProgress(progress: StoredProgress) {
        val normalized = normalizeProgress(progress)
        preferences.edit().apply {
            putString("$PROGRESS_PREFIX.level", normalized.level.name)
            putInt("$PROGRESS_PREFIX.index", normalized.index)
            putInt("$PROGRESS_PREFIX.lettersPerFragment", normalized.lettersPerFragment)
            SchoolLevel.values().forEach { level ->
                putInt("$PROGRESS_PREFIX.cursor.${level.name}", normalized.cursors.getValue(level))
            }
        }.apply()
    }

    fun readLeaderboard(): List<LeaderboardEntry> {
        val stored = preferences.getString(LEADERBOARD_KEY, null)
            ?: preferences.getString(LEGACY_LEADERBOARD_KEY, "")
            .orEmpty()
        return decodeLeaderboard(stored)
    }

    fun saveScore(entry: LeaderboardEntry): List<LeaderboardEntry> {
        val entries = sortLeaderboard(readLeaderboard() + entry)
        preferences.edit().putString(LEADERBOARD_KEY, encodeLeaderboard(entries)).apply()
        return entries
    }

    /** Compatibility overload for the existing ViewModel. */
    fun saveScore(score: Int): List<LeaderboardEntry> {
        val now = System.currentTimeMillis()
        val normalizedScore = score.coerceIn(0, MAX_SCORE)
        return saveScore(
            LeaderboardEntry(
                id = "$now-$normalizedScore-${readLeaderboard().size}",
                score = normalizedScore,
                createdAt = now,
            ),
        )
    }
}

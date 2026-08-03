package com.baptiste.dicta.beta.data

import android.content.Context

data class LeaderboardEntry(val score: Int, val createdAt: Long)

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("dicta-beta", Context.MODE_PRIVATE)

    fun readLeaderboard(): List<LeaderboardEntry> = preferences.getString("leaderboard", "")
        .orEmpty()
        .split(';')
        .mapNotNull { row ->
            val parts = row.split('|')
            if (parts.size != 2) null else runCatching { LeaderboardEntry(parts[0].toInt(), parts[1].toLong()) }.getOrNull()
        }
        .filter { it.score in 0..100 }
        .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.createdAt })
        .take(5)

    fun saveScore(score: Int) {
        val entries = (readLeaderboard() + LeaderboardEntry(score.coerceIn(0, 100), System.currentTimeMillis()))
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.createdAt })
            .take(5)
        preferences.edit().putString("leaderboard", entries.joinToString(";") { "${it.score}|${it.createdAt}" }).apply()
    }
}

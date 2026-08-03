package com.baptiste.dicta.beta.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val versionName: String, val downloadUrl: String, val releaseUrl: String)

class GitHubReleaseUpdateChecker {
    fun check(currentVersion: String): AppUpdate? {
        val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Copy-Challenge-Android/$currentVersion")
            if (connection.responseCode !in 200..299) return null
            parseLatestBeta(connection.inputStream.bufferedReader().use { it.readText() }, currentVersion)
        } finally { connection.disconnect() }
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/baptistefauchery-crypto/copy-chalenge/releases?per_page=10"

        fun parseLatestBeta(json: String, currentVersion: String): AppUpdate? {
            val releases = JSONArray(json)
            var latest: AppUpdate? = null
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft") || !release.optBoolean("prerelease")) continue
                val version = release.optString("tag_name").removePrefix("v")
                if (compareVersions(version, currentVersion) <= 0) continue
                val assets = release.optJSONArray("assets") ?: continue
                var downloadUrl: String? = null
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.getJSONObject(assetIndex)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (downloadUrl.isNullOrBlank()) continue
                val candidate = AppUpdate(version, downloadUrl, release.optString("html_url"))
                if (latest == null || compareVersions(candidate.versionName, latest.versionName) > 0) latest = candidate
            }
            return latest
        }

        fun compareVersions(left: String, right: String): Int {
            val leftParts = versionParts(left)
            val rightParts = versionParts(right)
            for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return 0
        }

        private fun versionParts(version: String) = Regex("\\d+").findAll(version.removePrefix("v")).map { it.value.toInt() }.toList()
    }
}

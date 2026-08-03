package com.baptiste.dicta.beta.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdateCheckerTest {
    @Test fun selectsNewestNonDraftBetaContainingAnApk() {
        val json = """[{"tag_name":"v0.1.0-beta.2","draft":false,"prerelease":true,"html_url":"https://example/release2","assets":[{"name":"copy-challenge-beta.apk","browser_download_url":"https://example/beta2.apk"}]},{"tag_name":"v0.1.0-beta.3","draft":true,"prerelease":true,"assets":[{"name":"app.apk","browser_download_url":"https://example/beta3.apk"}]}]"""
        val update = GitHubReleaseUpdateChecker.parseLatestBeta(json, "0.1.0-beta.1")
        assertEquals("0.1.0-beta.2", update?.versionName)
        assertEquals("https://example/beta2.apk", update?.downloadUrl)
    }

    @Test fun ignoresCurrentVersionAndReleasesWithoutApk() {
        val json = """[{"tag_name":"v0.1.0-beta.1","draft":false,"prerelease":true,"assets":[{"name":"app.apk","browser_download_url":"https://example/app.apk"}]},{"tag_name":"v0.1.0-beta.2","draft":false,"prerelease":true,"assets":[]}]"""
        assertNull(GitHubReleaseUpdateChecker.parseLatestBeta(json, "0.1.0-beta.1"))
    }

    @Test fun comparesBetaVersionsNumerically() {
        assertTrue(GitHubReleaseUpdateChecker.compareVersions("0.1.0-beta.10", "0.1.0-beta.2") > 0)
    }
}

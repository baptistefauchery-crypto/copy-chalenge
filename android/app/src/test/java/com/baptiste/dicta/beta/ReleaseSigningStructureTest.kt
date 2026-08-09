package com.baptiste.dicta.beta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningStructureTest {
    private val androidRoot = File("..")
    private val repositoryRoot = File("../..")

    @Test
    fun trustedCertificateAndKeystoreStayPubliclyVerifiableButUntracked() {
        val certificateFingerprint = androidRoot.resolve("signing-cert-sha256.txt").readText().trim()
        val keystoreFingerprint = androidRoot.resolve("keystore-file-sha256.txt").readText().trim()
        val gitignore = repositoryRoot.resolve(".gitignore").readText()
        val verifier = androidRoot.resolve("scripts/verify-android-signing.ps1").readText()

        assertTrue(certificateFingerprint.matches(Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")))
        assertTrue(keystoreFingerprint.matches(Regex("[0-9A-F]{64}")))
        assertTrue(gitignore.contains("android/*.keystore"))
        assertTrue(verifier.contains("certificate SHA-256 digest"))
        assertTrue(verifier.contains("Signing certificate mismatch"))
        assertFalse(verifier.contains("keytool -genkey"))
    }

    @Test
    fun taggedReleaseCannotPublishBeforeVersionKeyAndCertificateChecks() {
        val workflow = repositoryRoot.resolve(".github/workflows/android-beta-release.yml").readText()
        val versionCheck = workflow.indexOf("verify-beta-version.ps1")
        val secretCheck = workflow.indexOf("Missing DICTA_BETA_KEYSTORE_BASE64")
        val restore = workflow.indexOf("base64 --decode > android/beta-debug.keystore")
        val build = workflow.indexOf("testBetaReleaseUnitTest lintBetaRelease assembleBetaRelease")
        val certificateCheck = workflow.indexOf("verify-android-signing.ps1")
        val release = workflow.indexOf("gh release create")

        assertTrue(workflow.contains("secrets.DICTA_BETA_KEYSTORE_BASE64"))
        assertTrue(workflow.contains("available=false"))
        assertTrue(workflow.contains("steps.signing_key.outputs.available == 'true'"))
        assertTrue(workflow.contains("\${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"))
        assertTrue(workflow.contains("keystore-file-sha256.txt"))
        assertTrue(workflow.contains("java-version: \"17\""))
        assertTrue(workflow.contains("app-beta-release.apk"))
        assertTrue(workflow.contains("git merge-base --is-ancestor \"\$GITHUB_SHA\" origin/main"))
        assertFalse(workflow.contains("uses: actions/checkout@v"))
        assertFalse(workflow.contains("uses: actions/setup-java@v"))
        assertTrue(versionCheck >= 0)
        assertTrue(secretCheck < versionCheck)
        assertTrue(versionCheck < restore)
        assertTrue(restore < build)
        assertTrue(build < certificateCheck)
        assertTrue(certificateCheck < release)
        assertFalse(workflow.contains("keytool -genkey"))
    }

    @Test
    fun versionGuardRequiresTagVersionNameAndVersionCodeToAgree() {
        val guard = androidRoot.resolve("scripts/verify-beta-version.ps1").readText()

        assertTrue(guard.contains("vMAJOR.MINOR.PATCH-beta.NUMBER"))
        assertTrue(guard.contains("versionName"))
        assertTrue(guard.contains("versionCode"))
        assertTrue(guard.contains("historical beta maximum"))
        assertTrue(guard.contains("CheckGitHistory"))
        assertTrue(guard.contains("Version/tag mismatch"))
    }
}

package com.baptiste.dicta.beta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the in-app APK update flow against reopening GitHub or asking users to uninstall. */
class InAppUpdateStructureTest {
    private val mainSource = File("src/main")

    private fun kotlinSources(): String = mainSource.resolve("java")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    @Test
    fun manifestAllowsPackageInstallerUpdatesWithoutDeletePermissions() {
        val manifest = mainSource.resolve("AndroidManifest.xml").readText()

        assertTrue(
            "APK installation permission is required",
            manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"),
        )
        listOf(
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
            "android.permission.REQUEST_DELETE_PACKAGES",
            "android.permission.DELETE_PACKAGES",
        ).forEach { forbidden ->
            assertFalse("The updater must never request uninstall permission: $forbidden", manifest.contains(forbidden))
        }
    }

    @Test
    fun updaterUsesPackageInstallerAndHandlesPendingUserConfirmation() {
        val sources = kotlinSources()

        assertTrue("The updater must use Android PackageInstaller", sources.contains("PackageInstaller"))
        assertTrue(
            "PackageInstaller must handle Android's user-confirmation status",
            sources.contains("STATUS_PENDING_USER_ACTION"),
        )
        assertTrue("The APK session must be committed", sources.contains(".commit("))

        val opensDownloadInBrowser = Regex(
            """Intent\s*\(\s*Intent\.ACTION_VIEW\s*,\s*Uri\.parse\s*\(""",
        )
        assertFalse(
            "The update button must download internally instead of opening GitHub",
            opensDownloadInBrowser.containsMatchIn(sources),
        )
        listOf(
            "ACTION_UNINSTALL_PACKAGE",
            "ACTION_DELETE",
            "packageInstaller.uninstall",
            ".uninstall(",
        ).forEach { forbidden ->
            assertFalse("The updater must not request app removal: $forbidden", sources.contains(forbidden))
        }
    }

    @Test
    fun fileProviderIsCompleteOnlyWhenUsedAsAnInstallerFallback() {
        val sources = kotlinSources()
        if (!sources.contains("FileProvider")) return

        val manifest = mainSource.resolve("AndroidManifest.xml").readText()
        val providerBlock = manifest
            .substringAfter("androidx.core.content.FileProvider", missingDelimiterValue = "")
            .substringBefore("</provider>", missingDelimiterValue = "")
        assertTrue("FileProvider fallback must be declared in the manifest", providerBlock.isNotEmpty())
        assertTrue(providerBlock.contains("android:exported=\"false\""))
        assertTrue(providerBlock.contains("android:grantUriPermissions=\"true\""))

        val pathsName = Regex("""android:resource="@xml/([^"]+)"""")
            .find(providerBlock)
            ?.groupValues
            ?.get(1)
        assertTrue("FileProvider must reference an XML paths resource", !pathsName.isNullOrBlank())
        assertTrue(
            "Referenced FileProvider paths resource is missing",
            mainSource.resolve("res/xml/$pathsName.xml").isFile,
        )
    }

    @Test
    fun updateControlsAndStatusLabelsStayInsideTheInfoMenu() {
        val ui = mainSource.resolve("java/com/baptiste/dicta/beta/DictaApp.kt").readText()
        val helpStart = ui.indexOf("private fun HelpPanel")
        val helpEnd = ui.indexOf("private fun HomeIllustration", startIndex = helpStart)
        assertTrue("Help panel must exist", helpStart >= 0)
        assertTrue("Help panel boundary must remain detectable", helpEnd > helpStart)

        val helpRegion = ui.substring(helpStart, helpEnd)
        val outsideHelp = ui.removeRange(helpStart, helpEnd)
        assertTrue(helpRegion.contains("UpdateSection("))
        assertTrue(
            "Download action needs an explicit label",
            helpRegion.contains("Télécharger la mise à jour") || helpRegion.contains("Télécharger et installer"),
        )
        assertTrue("Download progress needs an explicit label", helpRegion.contains("Téléchargement"))
        assertTrue(
            "Install action needs an explicit label",
            helpRegion.contains("Installer la mise à jour") || helpRegion.contains("Installer maintenant"),
        )

        listOf(
            "Télécharger la mise à jour",
            "Télécharger et installer",
            "Téléchargement",
            "Installer la mise à jour",
            "Installer maintenant",
        ).forEach { updateCopy ->
            assertFalse("Update UI must stay inside the i menu: $updateCopy", outsideHelp.contains(updateCopy))
        }
    }
}

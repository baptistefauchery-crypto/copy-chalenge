package com.baptiste.dicta.beta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIdentityStructureTest {
    private val mainSource = File("src/main")

    @Test
    fun manifestKeepsStableIdentityPortraitAndUpdateNetworkContract() {
        val manifest = mainSource.resolve("AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:label=\"Copy Challenge\""))
        assertTrue(manifest.contains("android:icon=\"@drawable/ic_copy_challenge\""))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.INTERNET\" />"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE\" tools:node=\"remove\""))
        assertFalse(manifest.contains("android:label=\"bêta copy chalenge\""))
    }

    @Test
    fun resourcesKeepStableLightPalette() {
        val colors = mainSource.resolve("res/values/colors.xml").readText()
        val styles = mainSource.resolve("res/values/styles.xml").readText()
        val stylesV27 = mainSource.resolve("res/values-v27/styles.xml").readText()
        val stylesV29 = mainSource.resolve("res/values-v29/styles.xml").readText()

        assertTrue(colors.contains("<color name=\"dicta_paper\">#F2C8A7</color>"))
        assertTrue(colors.contains("<color name=\"dicta_ink\">#25233B</color>"))
        assertTrue(colors.contains("<color name=\"dicta_violet\">#6654D9</color>"))
        assertTrue(colors.contains("<color name=\"dicta_coral\">#EF765F</color>"))
        assertTrue(styles.contains("<item name=\"android:colorAccent\">@color/dicta_violet</item>"))
        assertTrue(stylesV27.contains("<item name=\"android:windowLightNavigationBar\">true</item>"))
        assertTrue(stylesV29.contains("<item name=\"android:forceDarkAllowed\">false</item>"))
    }
}

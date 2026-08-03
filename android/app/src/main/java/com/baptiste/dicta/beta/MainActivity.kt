package com.baptiste.dicta.beta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DictaTheme { DictaApp() } }
    }
}

@Composable
private fun DictaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val lightColors = lightColorScheme(
        primary = Color(0xFF163A32),
        onPrimary = Color.White,
        secondary = Color(0xFFB56B3C),
        background = Color(0xFFF7F4ED),
        surface = Color(0xFFF7F4ED),
        onBackground = Color(0xFF1F2D29),
        onSurface = Color(0xFF1F2D29),
    )
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColors, content = content)
}

package com.baptiste.dicta.beta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DictaTheme { DictaApp() } }
    }
}

@Composable
private fun DictaTheme(content: @Composable () -> Unit) {
    val stableColors = lightColorScheme(
        primary = colorResource(R.color.dicta_violet),
        onPrimary = colorResource(R.color.dicta_white),
        primaryContainer = colorResource(R.color.dicta_violet_soft),
        onPrimaryContainer = colorResource(R.color.dicta_violet_dark),
        secondary = colorResource(R.color.dicta_coral),
        onSecondary = colorResource(R.color.dicta_white),
        tertiary = colorResource(R.color.dicta_success),
        onTertiary = colorResource(R.color.dicta_ink),
        background = colorResource(R.color.dicta_paper),
        onBackground = colorResource(R.color.dicta_ink),
        surface = colorResource(R.color.dicta_paper_strong),
        onSurface = colorResource(R.color.dicta_ink),
        surfaceVariant = colorResource(R.color.dicta_violet_soft),
        onSurfaceVariant = colorResource(R.color.dicta_muted),
        outline = colorResource(R.color.dicta_muted),
        error = colorResource(R.color.dicta_error),
        onError = colorResource(R.color.dicta_white),
    )
    MaterialTheme(colorScheme = stableColors, content = content)
}

package com.dallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = GreenPrimary,
    onSecondary = GreenOnPrimary
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FCB9F),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF0F5C42),
    onPrimaryContainer = Color(0xFFD1F0E3),
    secondary = Color(0xFF6FCB9F),
    onSecondary = Color(0xFF003825)
)

@Composable
fun DalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

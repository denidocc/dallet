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
    onSecondary = GreenOnPrimary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ExpenseRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = IncomeGreenDark,
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF0F5C42),
    onPrimaryContainer = Color(0xFFD1F0E3),
    secondary = IncomeGreenDark,
    onSecondary = Color(0xFF003825),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ExpenseRedDark,
    onError = Color(0xFF3B0000)
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

@Composable
fun incomeAmountColor(): Color =
    if (isSystemInDarkTheme()) IncomeGreenDark else IncomeGreen

@Composable
fun expenseAmountColor(): Color =
    MaterialTheme.colorScheme.error

package com.bolkisan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = GreenDark,
    secondary = EarthBrown,
    onSecondary = Color.White,
    background = WarmCream,
    onBackground = SlateTextDark,
    surface = CardBackground,
    onSurface = SlateTextDark,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun BolKisanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = BolKisanTypography,
        content = content
    )
}

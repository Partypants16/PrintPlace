package com.printplace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrintPlaceBlue = Color(0xFF1B5E8A)
private val PrintPlaceBlueDark = Color(0xFF8FCBF0)

private val LightColors = lightColorScheme(
    primary = PrintPlaceBlue,
    secondary = Color(0xFF4C7A99),
)

private val DarkColors = darkColorScheme(
    primary = PrintPlaceBlueDark,
    secondary = Color(0xFFA9CFE6),
)

@Composable
fun PrintPlaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}

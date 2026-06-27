package com.stevanovicm32.mobilnoracunarstvo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF003910),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun MobilnoRacunarstvoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        content = content,
    )
}

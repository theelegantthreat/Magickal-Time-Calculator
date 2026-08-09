package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicDarkColorScheme = darkColorScheme(
    primary = VibrantAccentPurple,
    secondary = CosmicPurple,
    tertiary = VibrantMint,
    background = SpaceBackground,
    surface = StarrySlateCard,
    onBackground = CelestialText,
    onSurface = CelestialText,
    surfaceVariant = StarrySlateBorders,
    outline = StarrySlateBorders
)

private val ParchmentLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = CosmicPurple,
    tertiary = Color(0xFF533F72),
    background = Color(0xFFF9F6EE), // Elegant soft parchment cream
    surface = Color(0xFFF1EDE0),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE5DDD0),
    outline = Color(0xFFCAC4D0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CosmicDarkColorScheme else ParchmentLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.gayatripatel.callbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5EAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF00696E),
    secondaryContainer = Color(0xFFB4F0F2),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF7A4F9E),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceContainerLow = Color(0xFFF1F3FA),
    surfaceContainer = Color(0xFFEBEEF5),
    surfaceContainerHigh = Color(0xFFE5E8EF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003064),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF84D4D7),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFFB4F0F2),
    tertiary = Color(0xFFDDB8FF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF10131A),
    surface = Color(0xFF10131A),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF272A31),
)

/** Colours used to tell the user's lines apart (Work, Personal…). */
val LineColors = listOf(Color(0xFF1F5EAD), Color(0xFF00897B), Color(0xFF8E24AA), Color(0xFFEF6C00), Color(0xFFD81B60))

val CallGreen = Color(0xFF1E8E3E)
val CallRed = Color(0xFFD93025)

@Composable
fun CallBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

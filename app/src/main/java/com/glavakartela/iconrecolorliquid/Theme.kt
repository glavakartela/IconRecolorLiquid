package com.glavakartela.iconrecolorliquid

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Material color scheme built from a seed color. */
fun appColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val (_, chroma, hue) = Tonal.toLch(seed)
    fun t(tone: Double, k: Double = 1.0) = Color(Tonal.color(tone, (chroma * k).coerceAtMost(48.0), hue))
    return if (dark) {
        darkColorScheme(
            primary = t(80.0, 0.8),
            onPrimary = t(20.0, 0.8),
            primaryContainer = t(30.0, 0.8),
            onPrimaryContainer = t(90.0, 0.4),
            secondary = t(80.0, 0.35),
            onSecondary = t(20.0, 0.35),
            secondaryContainer = t(30.0, 0.35),
            onSecondaryContainer = t(90.0, 0.25),
            tertiary = t(80.0, 0.5),
            onTertiary = t(20.0, 0.5),
            tertiaryContainer = t(30.0, 0.5),
            onTertiaryContainer = t(90.0, 0.3),
            background = t(6.0, 0.12),
            onBackground = t(90.0, 0.08),
            surface = t(6.0, 0.12),
            onSurface = t(90.0, 0.08),
            surfaceVariant = t(30.0, 0.18),
            onSurfaceVariant = t(80.0, 0.18),
            surfaceContainerLowest = t(4.0, 0.12),
            surfaceContainerLow = t(10.0, 0.12),
            surfaceContainer = t(12.0, 0.12),
            surfaceContainerHigh = t(17.0, 0.12),
            surfaceContainerHighest = t(22.0, 0.12),
            outline = t(60.0, 0.18),
            outlineVariant = t(30.0, 0.18),
        )
    } else {
        lightColorScheme(
            primary = t(40.0, 0.8),
            onPrimary = t(100.0, 0.0),
            primaryContainer = t(90.0, 0.8),
            onPrimaryContainer = t(10.0, 0.6),
            secondary = t(40.0, 0.35),
            onSecondary = t(100.0, 0.0),
            secondaryContainer = t(90.0, 0.35),
            onSecondaryContainer = t(10.0, 0.3),
            tertiary = t(40.0, 0.5),
            onTertiary = t(100.0, 0.0),
            tertiaryContainer = t(90.0, 0.5),
            onTertiaryContainer = t(10.0, 0.4),
            background = t(98.0, 0.08),
            onBackground = t(10.0, 0.08),
            surface = t(98.0, 0.08),
            onSurface = t(10.0, 0.08),
            surfaceVariant = t(90.0, 0.18),
            onSurfaceVariant = t(30.0, 0.18),
            surfaceContainerLowest = t(100.0, 0.0),
            surfaceContainerLow = t(96.0, 0.1),
            surfaceContainer = t(94.0, 0.12),
            surfaceContainerHigh = t(92.0, 0.12),
            surfaceContainerHighest = t(90.0, 0.12),
            outline = t(50.0, 0.18),
            outlineVariant = t(80.0, 0.18),
        )
    }
}

package com.triledge.dailyjournal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Root theme. Apply once near the top of the UI tree. Color scheme is derived
 * from the user's chosen [seedColor]; shape style drives Material 3 [Shapes];
 * appearance mode toggles between System / Light / Dark / AMOLED.
 *
 * Design philosophy: dark-first glassmorphism. Deep navy-black backgrounds,
 * frosted-glass surfaces, vivid accent glow. Light mode is clean and airy.
 */
@Composable
fun TriledgeTheme(
    appearanceMode: AppearanceMode = AppearanceMode.Dark,
    seedColor: BrandColor = BrandPalette.Default,
    shapeStyle: ShapeStyle = ShapeStyle.Rounded,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appearanceMode) {
        AppearanceMode.Light -> false
        AppearanceMode.Dark, AppearanceMode.Amoled -> true
        AppearanceMode.System -> systemDark
    }
    val amoled = appearanceMode == AppearanceMode.Amoled

    val colorScheme = buildColorScheme(seedColor.seed, isDark, amoled)
    val shapes = shapeStyle.toShapes()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TriledgeTypography,
        shapes = shapes,
        content = content
    )
}

/**
 * Glassmorphism-ready color scheme.
 *
 * Dark mode uses deep navy (#0A0D14) as the canonical background so
 * frosted-glass cards (bg.copy(alpha=0.25)) read elegantly against it.
 * Accent/primary is the seed colour at full saturation — vivid against dark.
 */
private fun buildColorScheme(seed: Color, isDark: Boolean, amoled: Boolean): ColorScheme {
    fun lighten(c: Color, t: Float) = lerp(c, Color.White, t)
    fun darken(c: Color, t: Float) = lerp(c, Color.Black, t)

    val isLuxe = seed == Color(0xFF087E8B)

    // ── Backgrounds ──────────────────────────────────────────────────────────
    val background = when {
        isDark && amoled -> Color(0xFF000000)
        // Deep navy-black — perfect canvas for glass cards
        isDark           -> Color(0xFF080B12)
        isLuxe           -> Color(0xFFF2E9E4)
        else             -> Color(0xFFF4F6FA)
    }

    val onBackground = when {
        isDark  -> Color(0xFFE8ECF5)
        isLuxe  -> Color(0xFF291F1E)
        else    -> Color(0xFF0D1117)
    }

    // ── Surfaces (glass layer above background) ────────────────────────────
    val surface = when {
        isDark && amoled -> Color(0xFF050507)
        isDark           -> Color(0xFF0F1320)   // deep blue-grey glass surface
        isLuxe           -> Color(0xFFFFFFFF)
        else             -> Color(0xFFFFFFFF)
    }

    val onSurface = when {
        isDark  -> Color(0xFFE8ECF5)
        isLuxe  -> Color(0xFF291F1E)
        else    -> Color(0xFF111827)
    }

    val surfaceVariant = when {
        isDark  -> Color(0xFF161C2D)   // slightly lighter than surface for cards
        isLuxe  -> Color(0xFFE8DFDB)
        else    -> Color(0xFFEEF1F7)
    }

    val onSurfaceVariant = when {
        isDark  -> Color(0xFF8B95AD)
        isLuxe  -> Color(0xFF5A4D4A)
        else    -> Color(0xFF4B5563)
    }

    val outline = when {
        isDark  -> Color(0xFF232B3E)   // subtle glass border
        isLuxe  -> Color(0xFFC9ADA7)
        else    -> Color(0xFFD1D5DB)
    }

    val outlineVariant = when {
        isDark  -> Color(0xFF1A2132)
        isLuxe  -> Color(0xFFE8DFDB)
        else    -> Color(0xFFE5E7EB)
    }

    // ── Primary (accent) ──────────────────────────────────────────────────
    val primary = seed
    val onPrimary = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)

    val primaryContainer = when {
        isLuxe && isDark -> Color(0xFF0E2F33)
        isLuxe           -> Color(0xFFD2EFF2)
        isDark           -> darken(seed, 0.60f)
        else             -> lighten(seed, 0.85f)
    }
    val onPrimaryContainer = when {
        isLuxe && isDark -> Color(0xFFB8E0E4)
        isLuxe           -> Color(0xFF043439)
        isDark           -> lighten(seed, 0.75f)
        else             -> darken(seed, 0.55f)
    }

    // ── Secondary ──────────────────────────────────────────────────────────
    val secondary = when {
        isLuxe  -> Color(0xFFC9ADA7)
        isDark  -> lighten(seed, 0.08f)
        else    -> darken(seed, 0.08f)
    }
    val onSecondary = if (isDark) Color(0xFF050507) else Color(0xFFFFFFFF)
    val secondaryContainer = when {
        isLuxe && isDark -> Color(0xFF3A302E)
        isLuxe           -> Color(0xFFF3EDE9)
        isDark           -> darken(secondary, 0.60f)
        else             -> lighten(secondary, 0.88f)
    }
    val onSecondaryContainer = when {
        isLuxe && isDark -> Color(0xFFF3EDE9)
        isLuxe           -> Color(0xFF4E4240)
        isDark           -> lighten(secondary, 0.75f)
        else             -> darken(secondary, 0.55f)
    }

    // ── Tertiary ────────────────────────────────────────────────────────────
    val tertiary = when {
        isLuxe  -> Color(0xFFAB0D69)
        isDark  -> lighten(seed, 0.28f)
        else    -> darken(seed, 0.18f)
    }
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = when {
        isLuxe && isDark -> Color(0xFF450528)
        isLuxe           -> Color(0xFFFCD3E6)
        isDark           -> darken(tertiary, 0.60f)
        else             -> lighten(tertiary, 0.85f)
    }
    val onTertiaryContainer = when {
        isLuxe && isDark -> Color(0xFFFCD3E6)
        isLuxe           -> Color(0xFF550634)
        isDark           -> lighten(tertiary, 0.78f)
        else             -> darken(tertiary, 0.55f)
    }

    // ── Error ────────────────────────────────────────────────────────────────
    val error = if (isDark) Color(0xFFFF6B6B) else Color(0xFFDC2626)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = if (isDark) Color(0xFF5C1919) else Color(0xFFFEE2E2)
    val onErrorContainer = if (isDark) Color(0xFFFFB4B4) else Color(0xFF991B1B)

    return if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline, outlineVariant = outlineVariant
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline, outlineVariant = outlineVariant
        )
    }
}
package com.customlauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import com.customlauncher.R
import com.customlauncher.core.model.DarkMode
import com.customlauncher.core.model.FontChoice
import com.customlauncher.core.model.ThemeConfig

/** The active user theme, for components that need more than MaterialTheme offers (icon shape, labels). */
val LocalLauncherTheme = compositionLocalOf { ThemeConfig() }

fun Long.toColor(): Color = Color(this.toInt())
fun Color.toArgbLong(): Long = this.toArgb().toLong() and 0xFFFFFFFFL

/** Black or white, whichever reads better on [background]. */
fun readableOn(background: Color): Color = if (background.luminance() > 0.5f) Color.Black else Color.White

/**
 * Maps the user's [ThemeConfig] onto Material 3. Every color/font is user-configurable;
 * Material You dynamic color is optional (Android 12+).
 */
@Composable
fun LauncherTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (config.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
    }
    val colors = config.colors
    val scheme = when {
        config.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> {
            val primary = colors.primary.toColor()
            val accent = colors.accent.toColor()
            val surface = colors.surface.toColor()
            val background = colors.background.toColor()
            val text = colors.text.toColor()
            val base = if (dark) darkColorScheme() else lightColorScheme()
            base.copy(
                primary = primary,
                onPrimary = readableOn(primary),
                primaryContainer = primary.copy(alpha = 0.35f),
                onPrimaryContainer = text,
                secondary = accent,
                onSecondary = readableOn(accent),
                secondaryContainer = accent.copy(alpha = 0.3f),
                onSecondaryContainer = text,
                tertiary = accent,
                background = background,
                onBackground = text,
                surface = surface.copy(alpha = 1f),
                onSurface = text,
                surfaceVariant = surface.copy(alpha = 1f),
                onSurfaceVariant = text.copy(alpha = 0.75f),
                surfaceContainer = surface.copy(alpha = 1f),
                surfaceContainerHigh = surface.copy(alpha = 1f),
                surfaceContainerHighest = surface.copy(alpha = 1f),
                surfaceContainerLow = surface.copy(alpha = 1f),
            )
        }
    }
    val family = remember(config.font, config.customFontPath) { fontFamilyFor(config) }
    val typography = remember(family) { Typography().withFont(family) }

    CompositionLocalProvider(LocalLauncherTheme provides config) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

fun fontFamilyFor(config: ThemeConfig): FontFamily = when (config.font) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.INTER -> FontFamily(Font(R.font.inter))
    FontChoice.LORA -> FontFamily(Font(R.font.lora))
    FontChoice.JETBRAINS_MONO -> FontFamily(Font(R.font.jetbrains_mono))
    FontChoice.COMFORTAA -> FontFamily(Font(R.font.comfortaa))
    FontChoice.CUSTOM -> config.customFontPath
        ?.let { runCatching { FontFamily(Typeface(android.graphics.Typeface.createFromFile(it))) }.getOrNull() }
        ?: FontFamily.Default
}

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

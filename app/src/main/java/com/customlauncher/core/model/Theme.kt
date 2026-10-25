package com.customlauncher.core.model

import kotlinx.serialization.Serializable

/** Colors are stored as ARGB longs (0xAARRGGBB) so the JSON stays portable. */
@Serializable
data class ColorScheme(
    val primary: Long = 0xFF3DAEE9,
    val accent: Long = 0xFF1D99F3,
    val surface: Long = 0xCC232629,
    val background: Long = 0xFF1B1E20,
    val text: Long = 0xFFEFF0F1,
)

@Serializable
enum class DarkMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class IconShape { CIRCLE, SQUIRCLE, SQUARE, TEARDROP, NONE }

/**
 * Bundled font choices. [CUSTOM] uses a .ttf the user imported (see [ThemeConfig.customFontPath]).
 */
@Serializable
enum class FontChoice(val label: String) {
    SYSTEM("System"), INTER("Inter"), LORA("Lora"), JETBRAINS_MONO("JetBrains Mono"),
    COMFORTAA("Comfortaa"), CUSTOM("Imported font")
}

@Serializable
data class ThemeConfig(
    val dynamicColor: Boolean = false,
    val darkMode: DarkMode = DarkMode.DARK,
    val colors: ColorScheme = ColorScheme(),
    val font: FontChoice = FontChoice.SYSTEM,
    val customFontPath: String? = null,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconSizeDp: Float = 52f,
    val showLabels: Boolean = true,
    val labelSizeSp: Float = 12f,
    /** Package name of an installed ADW/Nova icon pack, or null for system icons. */
    val iconPack: String? = null,
)

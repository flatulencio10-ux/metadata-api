package com.customlauncher.widget

/**
 * Declarative description of a widget's settings. The default
 * [LauncherWidget.ConfigScreen] renders these automatically, so most widgets never need
 * hand-written settings UI.
 */
sealed interface ConfigField {
    val key: String
    val label: String

    data class Text(
        override val key: String,
        override val label: String,
        val default: String = "",
        val hint: String = "",
        val multiline: Boolean = false,
    ) : ConfigField

    data class Toggle(override val key: String, override val label: String, val default: Boolean = false) : ConfigField

    /** [options] maps stored value → display label. */
    data class Choice(
        override val key: String,
        override val label: String,
        val options: List<Pair<String, String>>,
        val default: String = options.first().first,
    ) : ConfigField

    data class Slider(
        override val key: String,
        override val label: String,
        val min: Float,
        val max: Float,
        val default: Float,
        val steps: Int = 0,
    ) : ConfigField

    data class ColorPick(override val key: String, override val label: String, val default: Long) : ConfigField

    /** Pick a list of apps; stored as flattened ComponentNames. */
    data class Apps(override val key: String, override val label: String) : ConfigField

    /**
     * Secret value (API key). Stored in EncryptedSharedPreferences under [secureKey],
     * never in the layout JSON, so profiles can be shared safely.
     */
    data class Secret(override val key: String, override val label: String, val secureKey: String, val hint: String = "") : ConfigField

    /** Read-only help text. */
    data class Info(override val key: String, override val label: String) : ConfigField
}

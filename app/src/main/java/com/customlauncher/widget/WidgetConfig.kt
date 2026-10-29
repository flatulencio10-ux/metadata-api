package com.customlauncher.widget

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Immutable, typed view over a widget instance's JSON config.
 * Getters fall back to the schema default (or the given default) when a key is missing,
 * so adding new config fields never breaks existing layouts.
 */
data class WidgetConfig(val json: JsonObject = JsonObject(emptyMap())) {

    fun string(key: String, default: String = ""): String =
        (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: default

    fun bool(key: String, default: Boolean = false): Boolean =
        (json[key] as? JsonPrimitive)?.booleanOrNull ?: default

    fun int(key: String, default: Int = 0): Int =
        (json[key] as? JsonPrimitive)?.intOrNull ?: default

    fun float(key: String, default: Float = 0f): Float =
        (json[key] as? JsonPrimitive)?.floatOrNull ?: default

    fun long(key: String, default: Long = 0L): Long =
        (json[key] as? JsonPrimitive)?.longOrNull ?: default

    fun stringList(key: String): List<String> =
        (json[key] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: emptyList()

    fun with(key: String, value: String) = put(key, JsonPrimitive(value))
    fun with(key: String, value: Boolean) = put(key, JsonPrimitive(value))
    fun with(key: String, value: Number) = put(key, JsonPrimitive(value))
    fun with(key: String, value: List<String>) = put(key, JsonArray(value.map { JsonPrimitive(it) }))

    fun put(key: String, value: JsonElement) = WidgetConfig(JsonObject(json + (key to value)))

    /** Typed lookups that use the schema's default values. */
    fun string(field: ConfigField.Text) = string(field.key, field.default)
    fun string(field: ConfigField.Choice) = string(field.key, field.default)
    fun bool(field: ConfigField.Toggle) = bool(field.key, field.default)
    fun float(field: ConfigField.Slider) = float(field.key, field.default)
    fun long(field: ConfigField.ColorPick) = long(field.key, field.default)
    fun stringList(field: ConfigField.Apps) = stringList(field.key)
}

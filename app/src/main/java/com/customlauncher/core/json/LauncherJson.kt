package com.customlauncher.core.json

import kotlinx.serialization.json.Json

/** Single shared Json configuration for profiles, widget configs and API caches. */
val LauncherJson: Json = Json {
    ignoreUnknownKeys = true      // newer exports still import on older builds
    encodeDefaults = true         // exported files are self-describing
    prettyPrint = true
    explicitNulls = false
    coerceInputValues = true      // unknown enum values fall back to defaults
    classDiscriminator = "kind"
}

/** Compact variant for network caches. */
val CompactJson: Json = Json(LauncherJson) { prettyPrint = false }

package com.customlauncher.data.profile

import com.customlauncher.core.json.LauncherJson
import com.customlauncher.core.model.ActivityProfile
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Page

/** Encodes/decodes profiles and repairs anything an imported file could get wrong. */
object ProfileCodec {

    fun encode(profile: LayoutProfile): String =
        LauncherJson.encodeToString(LayoutProfile.serializer(), profile)

    /** @throws kotlinx.serialization.SerializationException / IllegalArgumentException on invalid input. */
    fun decode(text: String): LayoutProfile =
        normalize(LauncherJson.decodeFromString(LayoutProfile.serializer(), text))

    /** Guarantees the invariants the UI relies on: ≥1 activity, ≥1 page each, valid current id. */
    fun normalize(profile: LayoutProfile): LayoutProfile {
        val activities = profile.activities
            .ifEmpty { listOf(ActivityProfile(name = "Default")) }
            .map { act ->
                val pages = act.pages.ifEmpty { listOf(Page()) }
                act.copy(pages = pages, defaultPage = act.defaultPage.coerceIn(0, pages.lastIndex))
            }
        val current = activities.firstOrNull { it.id == profile.currentActivityId }?.id ?: activities.first().id
        return profile.copy(activities = activities, currentActivityId = current)
    }
}

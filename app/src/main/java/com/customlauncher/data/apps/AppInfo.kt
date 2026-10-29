package com.customlauncher.data.apps

import android.content.ComponentName
import android.os.UserHandle

/** A launchable activity, already merged with the user's overrides (label, hidden, …). */
data class AppInfo(
    /** Flattened ComponentName; the key used everywhere in the profile. */
    val key: String,
    val component: ComponentName,
    val label: String,
    val originalLabel: String,
    val packageName: String,
    /** Human-readable category from ApplicationInfo.category (Play category), or "Other". */
    val category: String,
    val installTime: Long,
    val updateTime: Long,
    val user: UserHandle,
    val hidden: Boolean = false,
    val customIconPath: String? = null,
)

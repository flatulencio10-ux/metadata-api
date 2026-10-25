package com.customlauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DrawerMode { GRID, LIST, CATEGORIES }

@Serializable
enum class DrawerSort(val label: String) {
    NAME("Name A–Z"), NAME_DESC("Name Z–A"), INSTALL_DATE("Recently installed"), UPDATE_DATE("Recently updated")
}

@Serializable
data class DrawerConfig(
    val mode: DrawerMode = DrawerMode.GRID,
    val columns: Int = 5,
    val sort: DrawerSort = DrawerSort.NAME,
    val showSearchBar: Boolean = true,
    val backgroundColor: Long = 0xE6141618,
)

/** Per-app customisation keyed by flattened ComponentName. */
@Serializable
data class AppOverride(
    val label: String? = null,
    val hidden: Boolean = false,
    /** Absolute path of a custom icon image copied into app storage. */
    val iconPath: String? = null,
    val category: String? = null,
)

@Serializable
data class AppFolder(
    val id: String,
    val name: String,
    val components: List<String> = emptyList(),
)

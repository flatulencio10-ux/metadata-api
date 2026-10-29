package com.customlauncher.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.Edge
import com.customlauncher.ui.home.reservedSpace
import androidx.compose.ui.zIndex
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.PageTransition
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.WallpaperEditor
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetRegistry
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.launch

/** Edit Mode chrome: toolbar on top, inspector for the selected widget at the bottom. */
@Composable
fun EditOverlay(profile: LayoutProfile, vm: LauncherViewModel, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    // Float the toolbar below any top panels so it never sits on top of one.
    val topPanels = reservedSpace(profile.currentActivity.panels).getValue(Edge.TOP)
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topPanels + 8.dp, start = 8.dp, end = 8.dp)
                .zIndex(10f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarButton(Icons.Filled.Add, "Widget") { vm.addWidgetSheetOpen = true }
                ToolbarButton(Icons.Filled.AddToPhotos, "Page") {
                    vm.addPage()
                    scope.launch { pagerState.animateScrollToPage(profile.currentActivity.pages.size) }
                }
                ToolbarButton(Icons.Filled.Wallpaper, "This page") { vm.pageSettingsOpen = true }
                ToolbarButton(Icons.Filled.Layers, "Layers") { vm.layersPanelOpen = true }
                val snap = profile.grid.snapEnabled
                ToolbarButton(if (snap) Icons.Filled.GridOn else Icons.Filled.GridOff, if (snap) "Snap ${profile.grid.snapDp.toInt()}dp" else "Free") {
                    // Cycle: fine snap 4dp → 8dp → 16dp → free placement.
                    vm.edit { p ->
                        val g = p.grid
                        p.copy(grid = when {
                            !g.snapEnabled -> g.copy(snapEnabled = true, snapDp = 4f)
                            g.snapDp < 8f -> g.copy(snapDp = 8f)
                            g.snapDp < 16f -> g.copy(snapDp = 16f)
                            else -> g.copy(snapEnabled = false)
                        })
                    }
                }
                ToolbarButton(Icons.Filled.Settings, "Settings") { vm.run(Action.OpenSettings) }
                ToolbarButton(Icons.Filled.Check, "Done") { vm.exitEditMode() }
            }
        }

        val selected = vm.selectedWidgetId?.let { profile.findWidget(it) }
        if (selected != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                WidgetInspector(selected.id, selected.type, vm.stylePreview?.takeIf { it.first == selected.id }?.second ?: selected.style, vm)
            }
        }
    }

    if (vm.addWidgetSheetOpen) AddWidgetSheet(vm)
    if (vm.pageSettingsOpen) PageSettingsDialog(profile, vm)
    if (vm.layersPanelOpen) LayersPanel(profile, vm)
}

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Style + layer + config controls for one widget. */
@Composable
private fun WidgetInspector(id: String, type: String, style: WidgetStyle, vm: LauncherViewModel) {
    val def = WidgetRegistry.find(type)
    fun preview(s: WidgetStyle) { vm.stylePreview = id to s }
    val commit = { vm.commitStylePreview() }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(def?.name ?: type, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            ToolbarButton(Icons.Filled.Tune, "Configure") { vm.configuringWidgetId = id }
            ToolbarButton(Icons.Filled.FlipToFront, "Front") { vm.bringToFront(id) }
            ToolbarButton(Icons.Filled.FlipToBack, "Back") { vm.sendToBack(id) }
            ToolbarButton(Icons.AutoMirrored.Filled.ArrowBack, "Prev page") { vm.moveWidgetToPage(id, -1) }
            ToolbarButton(Icons.AutoMirrored.Filled.ArrowForward, "Next page") { vm.moveWidgetToPage(id, +1) }
            ToolbarButton(Icons.Filled.ContentCopy, "Duplicate") { vm.duplicateWidget(id) }
            ToolbarButton(Icons.Filled.Delete, "Delete") { vm.deleteWidget(id) }
        }
        LabeledSlider("Opacity", style.opacity, 0.05f..1f, { preview(style.copy(opacity = it)) },
            format = { "%.0f%%".format(it * 100) }, onChangeFinished = commit)
        if (!style.useCardStyle) LabeledSlider("Corner radius", style.cornerRadiusDp, 0f..64f, { preview(style.copy(cornerRadiusDp = it)) },
            format = { "${it.toInt()} dp" }, onChangeFinished = commit)
        LabeledSlider("Padding", style.paddingDp, 0f..32f, { preview(style.copy(paddingDp = it)) },
            format = { "${it.toInt()} dp" }, onChangeFinished = commit)
        if (!style.useCardStyle) LabeledSlider("Background blur", style.blurDp, 0f..40f, { preview(style.copy(blurDp = it)) },
            format = { "${it.toInt()} dp" }, onChangeFinished = commit)
        com.customlauncher.ui.common.SwitchRow(
            "Use global card style", style.useCardStyle,
            { on -> vm.updateWidget(id) { w -> w.copy(style = w.style.copy(useCardStyle = on)) } },
            supporting = "Theme → Card style. Turn off to customise this widget.",
        )
        if (!style.useCardStyle) {
            ColorRow("Background tint", style.backgroundColor) { vm.updateWidget(id) { w -> w.copy(style = w.style.copy(backgroundColor = it)) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWidgetSheet(vm: LauncherViewModel) {
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = { vm.addWidgetSheetOpen = false }, containerColor = androidx.compose.ui.graphics.Color(0xFF16181C)) {
        androidx.compose.material3.TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ) {
            androidx.compose.material3.Tab(tab == 0, { tab = 0 }, text = { Text("Widgets") })
            androidx.compose.material3.Tab(tab == 1, { tab = 1 }, text = { Text("My stickers") })
        }
        if (tab == 0) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(WidgetRegistry.all, key = { it.id }) { widget ->
                    ListItem(
                        headlineContent = { Text(widget.name) },
                        supportingContent = { if (widget.description.isNotEmpty()) Text(widget.description) },
                        leadingContent = { Icon(widget.icon, null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = androidx.compose.ui.graphics.Color.White,
                            supportingColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.clickable {
                            vm.addWidgetSheetOpen = false
                            // Stickers need an image first.
                            if (widget.id == StickerWidget.id) vm.stickerSourceSheetOpen = true else vm.addWidget(widget.id)
                        },
                    )
                }
            }
        } else {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                com.customlauncher.ui.stickers.StickerLibraryGrid(vm) { asset ->
                    vm.addWidgetSheetOpen = false
                    vm.addSticker(asset)
                }
                androidx.compose.material3.TextButton(onClick = { vm.addWidgetSheetOpen = false; vm.stickerSourceSheetOpen = true }) {
                    Text("Import a new sticker…")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageSettingsDialog(profile: LayoutProfile, vm: LauncherViewModel) {
    val index = vm.currentPage
    val page = profile.currentActivity.pages.getOrNull(index) ?: return
    val pageCount = profile.currentActivity.pages.size
    AlertDialog(
        onDismissRequest = { vm.pageSettingsOpen = false },
        title = { Text("Page ${index + 1} of $pageCount") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.customlauncher.ui.common.SwitchRow(
                    "Infinite canvas", page.infinite, { on -> vm.updatePage(index) { it.copy(infinite = on) } },
                    supporting = "Pan with one finger, pinch to zoom. Use the page dots to change pages.",
                )
                com.customlauncher.ui.common.SwitchRow(
                    "Own grid overlay", page.grid != null,
                    { on -> vm.updatePage(index) { it.copy(grid = if (on) (profile.grid.copy(snapEnabled = true, showGrid = true)) else null) } },
                    supporting = "Optional snapping just for this page. Stickers always place freely.",
                )
                page.grid?.let { g ->
                    LabeledSlider("Grid step", g.snapDp, 4f..48f, { v -> vm.updatePage(index) { it.copy(grid = g.copy(snapDp = v)) } }, format = { "${it.toInt()} dp" })
                }
                Text("Transition", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PageTransition.entries.forEach { t ->
                        FilterChip(page.transition == t, { vm.updatePage(index) { it.copy(transition = t) } },
                            { Text(t.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                    }
                }
                WallpaperEditor(page.wallpaper, allowInherit = true, copyImage = { vm.copyToStorage(it, "wallpapers") }) { w ->
                    vm.updatePage(index) { it.copy(wallpaper = w) }
                }
                Row {
                    TextButton(onClick = { vm.movePage(index, -1) }, enabled = index > 0) { Text("Move left") }
                    TextButton(onClick = { vm.movePage(index, +1) }, enabled = index < pageCount - 1) { Text("Move right") }
                    TextButton(onClick = { vm.removePage(index); vm.pageSettingsOpen = false }, enabled = pageCount > 1) { Text("Delete") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.pageSettingsOpen = false }) { Text("Done") } },
    )
}

/** Widget settings dialog: renders the widget's ConfigScreen on a draft, saved on confirm. */
@Composable
fun WidgetConfigDialog(profile: LayoutProfile, vm: LauncherViewModel, instanceId: String) {
    val instance = profile.findWidget(instanceId) ?: run { vm.configuringWidgetId = null; return }
    val def = WidgetRegistry.find(instance.type) ?: run { vm.configuringWidgetId = null; return }
    var draft by remember(instanceId) { mutableStateOf(WidgetConfig(instance.config)) }
    AlertDialog(
        onDismissRequest = { vm.configuringWidgetId = null },
        title = { Text(def.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                def.ConfigScreen(draft) { draft = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateWidgetConfig(instanceId, draft)
                vm.configuringWidgetId = null
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.configuringWidgetId = null }) { Text("Cancel") } },
    )
}

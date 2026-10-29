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
        androidx.compose.animation.AnimatedVisibility(
            visible = selected != null,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f)) { it },
            exit = androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selected?.let { node ->
                com.customlauncher.ui.edit.inspector.NodeInspector(
                    node, profile, vm,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    }

    vm.clipRecording?.let { (id, clip) ->
        profile.findWidget(id)?.let { node ->
            com.customlauncher.ui.anim.ClipRecorderOverlay(vm, node, clip) { clips -> vm.updateWidget(id) { it.copy(clips = clips) } }
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
            androidx.compose.material3.Tab(tab == 2, { tab = 2 }, text = { Text("Objects") })
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
        } else if (tab == 2) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(com.customlauncher.objects.Presets.all, key = { it.id }) { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = { Text(preset.description) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = androidx.compose.ui.graphics.Color.White,
                            supportingColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.clickable { vm.addWidgetSheetOpen = false; vm.addPreset(preset) },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Physics sandbox page") },
                        supportingContent = { Text("New page with tilt gravity, balls, a spinner and a spawn button") },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.primary,
                            supportingColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier.clickable { vm.addWidgetSheetOpen = false; vm.addSandboxPage() },
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
                Text("Physics world", style = MaterialTheme.typography.titleSmall)
                val w = page.world
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.customlauncher.core.model.GravityDirection.entries.forEach { g ->
                        FilterChip(w.gravity == g && !w.followTilt, { vm.updatePage(index) { it.copy(world = w.copy(gravity = g, followTilt = false)) } }, { Text(g.label) })
                    }
                }
                LabeledSlider("Gravity strength", w.gravityStrength, 0f..30f, { v -> vm.updatePage(index) { it.copy(world = w.copy(gravityStrength = v)) } }, format = { "%.1f".format(it) })
                com.customlauncher.ui.common.SwitchRow("Gravity follows device tilt", w.followTilt, { on -> vm.updatePage(index) { it.copy(world = w.copy(followTilt = on)) } })
                com.customlauncher.ui.common.SwitchRow("Widgets and icons are solid", w.collideWithWidgets, { on -> vm.updatePage(index) { it.copy(world = w.copy(collideWithWidgets = on)) } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(w.wallLeft, { vm.updatePage(index) { it.copy(world = w.copy(wallLeft = !w.wallLeft)) } }, { Text("Left wall") })
                    FilterChip(w.wallRight, { vm.updatePage(index) { it.copy(world = w.copy(wallRight = !w.wallRight)) } }, { Text("Right wall") })
                    FilterChip(w.wallTop, { vm.updatePage(index) { it.copy(world = w.copy(wallTop = !w.wallTop)) } }, { Text("Top wall") })
                    FilterChip(w.wallBottom, { vm.updatePage(index) { it.copy(world = w.copy(wallBottom = !w.wallBottom)) } }, { Text("Floor") })
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

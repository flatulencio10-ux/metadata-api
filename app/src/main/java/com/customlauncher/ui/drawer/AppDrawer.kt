package com.customlauncher.ui.drawer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.AppFolder
import com.customlauncher.core.model.DrawerMode
import com.customlauncher.core.model.DrawerSort
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.newId
import com.customlauncher.data.apps.AppInfo
import com.customlauncher.data.apps.AppRepository
import com.customlauncher.data.apps.AppShortcut
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.AppPickerDialog
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.common.TextInputDialog
import com.customlauncher.ui.theme.LocalLauncherTheme
import com.customlauncher.ui.theme.toColor

/**
 * App drawer with grid / list / category modes, search, sorting, folders and per-app
 * customisation (rename, hide, custom icon, shortcuts).
 */
@Composable
fun AppDrawer(profile: LayoutProfile, vm: LauncherViewModel) {
    val config = profile.drawer
    val all = LocalAppCatalog.current.apps
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<AppFolder?>(null) }
    var editingFolder by remember { mutableStateOf<AppFolder?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val visible = remember(all, query, showHidden, config.sort) {
        AppRepository.sort(all.filter { (showHidden || !it.hidden) && (query.isBlank() || it.label.contains(query, true)) }, config.sort)
    }
    fun setDrawer(transform: (com.customlauncher.core.model.DrawerConfig) -> com.customlauncher.core.model.DrawerConfig) =
        vm.edit { it.copy(drawer = transform(it.drawer)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(config.backgroundColor.toColor())
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (config.showSearchBar) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search ${visible.size} apps") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                )
            } else Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Drawer options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DrawerSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text((if (config.sort == sort) "✓ " else "") + sort.label) },
                            onClick = { setDrawer { it.copy(sort = sort) }; menuOpen = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (showHidden) "Hide hidden apps" else "Show hidden apps") },
                        onClick = { showHidden = !showHidden; menuOpen = false })
                    DropdownMenuItem(text = { Text("New folder") },
                        onClick = { editingFolder = AppFolder(newId(), "Folder"); menuOpen = false })
                    DropdownMenuItem(text = { Text("Columns: ${config.columns} (tap to change)") },
                        onClick = { setDrawer { it.copy(columns = if (it.columns >= 7) 3 else it.columns + 1) } })
                }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(config.mode == DrawerMode.GRID, { setDrawer { it.copy(mode = DrawerMode.GRID) } }, { Text("Grid") },
                leadingIcon = { Icon(Icons.Filled.Apps, null) })
            FilterChip(config.mode == DrawerMode.LIST, { setDrawer { it.copy(mode = DrawerMode.LIST) } }, { Text("List") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) })
            FilterChip(config.mode == DrawerMode.CATEGORIES, { setDrawer { it.copy(mode = DrawerMode.CATEGORIES) } }, { Text("Categories") },
                leadingIcon = { Icon(Icons.Filled.Category, null) })
            profile.folders.forEach { folder ->
                AssistChip(onClick = { openFolder = folder }, label = { Text(folder.name) }, leadingIcon = { Icon(Icons.Filled.Folder, null) })
            }
            AssistChip(onClick = { editingFolder = AppFolder(newId(), "Folder") }, label = { Text("Folder") },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) })
        }

        val launch: (AppInfo) -> Unit = { app ->
            vm.run(Action.OpenApp(app.key))
            vm.drawerOpen = false
        }
        when (config.mode) {
            DrawerMode.GRID -> LazyVerticalGrid(GridCells.Fixed(config.columns.coerceIn(2, 8)), Modifier.fillMaxSize()) {
                items(visible, key = { it.key }) { app -> AppCell(app, vm, launch) }
            }
            DrawerMode.LIST -> LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.key }) { app -> AppRow(app, vm, launch) }
            }
            DrawerMode.CATEGORIES -> {
                val groups = remember(visible) { visible.groupBy { it.category }.toSortedMap() }
                LazyVerticalGrid(GridCells.Fixed(config.columns.coerceIn(2, 8)), Modifier.fillMaxSize()) {
                    groups.forEach { (category, apps) ->
                        item(key = "header-$category", span = { GridItemSpan(maxLineSpan) }) {
                            Text(category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(apps, key = { "$category-${it.key}" }) { app -> AppCell(app, vm, launch) }
                    }
                }
            }
        }
    }

    openFolder?.let { folder ->
        FolderDialog(folder, onDismiss = { openFolder = null }, onEdit = { editingFolder = folder; openFolder = null }) { key ->
            vm.run(Action.OpenApp(key)); openFolder = null; vm.drawerOpen = false
        }
    }
    editingFolder?.let { folder ->
        FolderEditor(folder, onDismiss = { editingFolder = null }, onDelete = { vm.deleteFolder(folder.id); editingFolder = null }) {
            vm.upsertFolder(it); editingFolder = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(app: AppInfo, vm: LauncherViewModel, launch: (AppInfo) -> Unit) {
    val theme = LocalLauncherTheme.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { launch(app) }, onLongClick = { menu = true })
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app.key, theme.iconSizeDp.dp)
            if (theme.showLabels) {
                Text(
                    app.label, fontSize = theme.labelSizeSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        AppMenu(app, menu, onDismiss = { menu = false }, vm = vm)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppInfo, vm: LauncherViewModel, launch: (AppInfo) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { launch(app) }, onLongClick = { menu = true })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.key, 40.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.label, color = MaterialTheme.colorScheme.onBackground)
                Text(app.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
        AppMenu(app, menu, onDismiss = { menu = false }, vm = vm)
    }
}

/** Long-press menu: shortcuts + customisation. */
@Composable
private fun AppMenu(app: AppInfo, expanded: Boolean, onDismiss: () -> Unit, vm: LauncherViewModel) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCustomIcon(app.key, uri)
    }
    val shortcuts by produceState<List<AppShortcut>>(emptyList(), expanded, app.packageName) {
        value = if (expanded) context.container.shortcutRepository.shortcutsFor(app.packageName) else emptyList()
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        shortcuts.take(5).forEach { s ->
            DropdownMenuItem(text = { Text(s.label) }, onClick = {
                vm.run(Action.OpenShortcut(s.packageName, s.id, s.label)); onDismiss(); vm.drawerOpen = false
            })
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(text = { Text("Add to home screen") }, onClick = { vm.addAppToHome(app.key); onDismiss() })
        DropdownMenuItem(text = { Text("Add to panel") }, onClick = { vm.addAppToPanel(app.key); onDismiss() })
        DropdownMenuItem(text = { Text("Rename") }, onClick = { renaming = true; onDismiss() })
        DropdownMenuItem(text = { Text("Change icon…") }, onClick = {
            iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)); onDismiss()
        })
        if (app.customIconPath != null) {
            DropdownMenuItem(text = { Text("Reset icon") }, onClick = { vm.updateOverride(app.key) { it.copy(iconPath = null) }; onDismiss() })
        }
        DropdownMenuItem(text = { Text(if (app.hidden) "Unhide" else "Hide") }, onClick = {
            vm.updateOverride(app.key) { it.copy(hidden = !app.hidden) }; onDismiss()
        })
        DropdownMenuItem(text = { Text("App info") }, onClick = { context.container.appRepository.openAppInfo(app.key); onDismiss() })
        DropdownMenuItem(text = { Text("Uninstall") }, onClick = { context.container.appRepository.uninstall(app.key); onDismiss() })
    }
    if (renaming) {
        TextInputDialog("Rename app", initial = app.label, label = "Empty = original name", onDismiss = { renaming = false }) { name ->
            vm.updateOverride(app.key) { it.copy(label = name.takeIf { n -> n.isNotBlank() && n != app.originalLabel }) }
            renaming = false
        }
    }
}

@Composable
private fun FolderDialog(folder: AppFolder, onDismiss: () -> Unit, onEdit: () -> Unit, onLaunch: (String) -> Unit) {
    val catalog = LocalAppCatalog.current
    val theme = LocalLauncherTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = {
            val keys = folder.components.filter { it in catalog.byKey }
            if (keys.isEmpty()) Text("Empty folder")
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 400.dp)) {
                items(keys) { key ->
                    Column(
                        Modifier
                            .clickable { onLaunch(key) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(key, theme.iconSizeDp.dp)
                        Text(catalog.label(key), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun FolderEditor(folder: AppFolder, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (AppFolder) -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    var components by remember { mutableStateOf(folder.components) }
    var picking by remember { mutableStateOf(false) }
    val catalog = LocalAppCatalog.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text(if (components.isEmpty()) "No apps" else components.joinToString { catalog.label(it) },
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { picking = true }) { Text("Choose apps") }
                TextButton(onClick = onDelete) { Text("Delete folder") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(folder.copy(name = name.ifBlank { "Folder" }, components = components)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        AppPickerDialog("Apps in folder", multi = true, initiallySelected = components, onDismiss = { picking = false }) {
            components = it; picking = false
        }
    }
}

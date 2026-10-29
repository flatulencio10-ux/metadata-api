package com.customlauncher.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Node
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.TextInputDialog
import com.customlauncher.widget.WidgetRegistry
import kotlin.math.abs

private val SheetBg = Color(0xFF16181C)

/**
 * Figma-style layers list for the current page (and pinned nodes): topmost first. Select,
 * rename, hide, lock, reorder, and group / ungroup several nodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersPanel(profile: LayoutProfile, vm: LauncherViewModel) {
    val activity = profile.currentActivity
    val page = activity.pages.getOrNull(vm.currentPage)
    var checked by remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<Node?>(null) }

    ModalBottomSheet(onDismissRequest = { vm.layersPanelOpen = false }, containerColor = SheetBg) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layers", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (checked.size >= 2) FilledTonalButton(onClick = { vm.group(checked); checked = emptySet() }) { Text("Group") }
                if (checked.isNotEmpty()) {
                    TextButton(onClick = { vm.ungroup(checked); checked = emptySet() }) { Text("Ungroup") }
                    TextButton(onClick = { checked.forEach { vm.deleteWidget(it) }; checked = emptySet() }) { Text("Delete") }
                }
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (activity.pinned.isNotEmpty()) {
                    item { Header("Pinned across all pages") }
                    items(activity.pinned.sortedByDescending { it.z }, key = { "p-" + it.id }) { n ->
                        LayerRow(n, pinned = true, checked = n.id in checked, selected = vm.selectedWidgetId == n.id, vm = vm,
                            onCheck = { checked = if (n.id in checked) checked - n.id else checked + n.id },
                            onRename = { renaming = n })
                    }
                }
                item { Header(page?.name?.ifBlank { null } ?: "Page ${vm.currentPage + 1}") }
                if (page == null || page.widgets.isEmpty()) item { Text("Nothing on this page yet.", color = Color.White.copy(alpha = 0.6f)) }
                items(page?.widgets.orEmpty().sortedByDescending { it.z }, key = { it.id }) { n ->
                    LayerRow(n, pinned = false, checked = n.id in checked, selected = vm.selectedWidgetId == n.id, vm = vm,
                        onCheck = { checked = if (n.id in checked) checked - n.id else checked + n.id },
                        onRename = { renaming = n })
                }
            }
        }
    }
    renaming?.let { n ->
        TextInputDialog("Layer name", initial = n.name, onDismiss = { renaming = null }) {
            vm.renameNode(n.id, it); renaming = null
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun LayerRow(
    n: Node,
    pinned: Boolean,
    checked: Boolean,
    selected: Boolean,
    vm: LauncherViewModel,
    onCheck: () -> Unit,
    onRename: () -> Unit,
) {
    val def = WidgetRegistry.find(n.type)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { vm.selectedWidgetId = n.id }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, { onCheck() }, colors = CheckboxDefaults.colors(uncheckedColor = Color.White.copy(alpha = 0.5f)))
        def?.icon?.let { Icon(it, null, tint = Color.White.copy(alpha = if (n.hidden) 0.35f else 0.85f), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(
            n.name.ifBlank { def?.name ?: n.type },
            color = Color.White.copy(alpha = if (n.hidden) 0.4f else 1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onRename),
        )
        n.groupId?.let { gid ->
            // Same colour = same group.
            Box(Modifier.size(10.dp).background(Color.hsv(abs(gid.hashCode() % 360).toFloat(), 0.6f, 0.95f), CircleShape))
        }
        if (pinned) Icon(Icons.Filled.PushPin, "Pinned", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        IconButton(onClick = { vm.toggleHidden(n.id) }) {
            Icon(if (n.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hide", tint = Color.White)
        }
        IconButton(onClick = { vm.toggleLocked(n.id) }) {
            Icon(if (n.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, "Lock", tint = if (n.locked) MaterialTheme.colorScheme.primary else Color.White)
        }
        Column(verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = Color.White, modifier = Modifier.size(22.dp).clickable { vm.moveZ(n.id, +1) })
            Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = Color.White, modifier = Modifier.size(22.dp).clickable { vm.moveZ(n.id, -1) })
        }
    }
}

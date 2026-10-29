package com.customlauncher.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Action
import com.customlauncher.desktop.StartMenuLogic
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.LocalAppCatalog

private val Glass = Color(0xE6181A20)
private val Accent = Color(0xFF7C9CFF)

/**
 * Desktop-style start menu: search box, category chips and an app grid. Opens with a springy
 * scale from the bottom-left corner, just above the panels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.StartMenu(vm: LauncherViewModel, bottomInset: Dp) {
    AnimatedVisibility(
        visible = vm.startMenuOpen,
        enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)),
        exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { vm.startMenuOpen = false }) {
            val catalog = LocalAppCatalog.current
            var query by remember { mutableStateOf("") }
            var category by remember { mutableStateOf<String?>(null) }
            val categories = remember(catalog) { StartMenuLogic.categories(catalog.apps.map { it.category }) }
            val shown = StartMenuLogic.search(catalog.apps.filter { category == null || it.category == category }, query) { it.label }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, end = 8.dp, bottom = bottomInset + 8.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Glass)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { }
                    .padding(14.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterStart))
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Search apps", color = Color.White.copy(alpha = 0.5f)); inner() },
                    )
                }
                LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { CategoryChip("All", category == null) { category = null } }
                    items(categories) { cat -> CategoryChip(cat, category == cat) { category = cat } }
                }
                LazyVerticalGrid(GridCells.Adaptive(76.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        var menu by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { vm.startMenuOpen = false; vm.run(Action.OpenApp(app.key)) },
                                    onLongClick = { menu = true },
                                )
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app.key, 44.dp)
                            Text(app.label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Open in window") }, onClick = { menu = false; vm.startMenuOpen = false; vm.openAppInWindow(app.key) })
                                DropdownMenuItem(text = { Text("Pin to taskbar") }, onClick = { menu = false; vm.toggleTaskbarPin(app.key) })
                                DropdownMenuItem(text = { Text("Add to home") }, onClick = { menu = false; vm.startMenuOpen = false; vm.addAppToHome(app.key) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected, onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Accent, selectedLabelColor = Color.White),
    )
}

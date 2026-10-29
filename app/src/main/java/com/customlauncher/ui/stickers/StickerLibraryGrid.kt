package com.customlauncher.ui.stickers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.data.stickers.StickerAsset
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.TextInputDialog
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.launch

/**
 * "My stickers": every imported sticker, reusable without importing again. Search by name,
 * filter favourites; long-press for favourite / rename / delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerLibraryGrid(vm: LauncherViewModel, onPicked: (StickerAsset) -> Unit) {
    val repo = LocalContext.current.container.stickerRepository
    val scope = rememberCoroutineScope()
    val library by repo.library.collectAsState()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<StickerAsset?>(null) }

    val shown = library
        .filter { !favoritesOnly || it.favorite }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<StickerAsset> { it.favorite }.thenByDescending { it.addedAt })

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My stickers", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            FilterChip(
                favoritesOnly, { favoritesOnly = !favoritesOnly }, { Text("Favourites") },
                leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, iconColor = Color.White),
            )
        }
        if (library.size > 6) {
            OutlinedTextField(
                query, { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White) },
                placeholder = { Text("Search by name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        if (shown.isEmpty()) {
            Text(
                if (library.isEmpty()) "Nothing imported yet." else "No matching stickers.",
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            return
        }
        LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
            items(shown, key = { it.id }) { asset ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .combinedClickable(onClick = { onPicked(asset) }, onLongClick = { menuFor = asset.id })
                        .padding(8.dp)
                ) {
                    StickerWidget.StickerImage(
                        repo.fileOf(asset.file), asset.kind, DpSize(76.dp, 76.dp), playing = false, modifier = Modifier.fillMaxSize(),
                    )
                    if (asset.favorite) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(14.dp).align(Alignment.TopEnd))
                    DropdownMenu(expanded = menuFor == asset.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text(if (asset.favorite) "Remove from favourites" else "Add to favourites") }, onClick = {
                            scope.launch { repo.update(asset.id) { it.copy(favorite = !it.favorite) } }; menuFor = null
                        })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { renaming = asset; menuFor = null })
                        DropdownMenuItem(text = { Text("Delete from library") }, onClick = {
                            scope.launch { repo.delete(asset.id) }; menuFor = null
                        })
                    }
                }
            }
        }
    }
    renaming?.let { asset ->
        TextInputDialog("Sticker name", initial = asset.name, onDismiss = { renaming = null }) { name ->
            scope.launch { repo.update(asset.id) { it.copy(name = name.trim()) } }
            renaming = null
        }
    }
}

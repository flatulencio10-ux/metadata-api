package com.customlauncher.ui.stickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.data.stickers.StickerAsset
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.widgets.sticker.StickerWidget

/** "My stickers": every imported sticker, reusable without importing again. */
@Composable
fun StickerLibraryGrid(vm: LauncherViewModel, onPicked: (StickerAsset) -> Unit) {
    val repo = LocalContext.current.container.stickerRepository
    val library by repo.library.collectAsState()
    Column {
        Text("My stickers", style = MaterialTheme.typography.titleMedium, color = Color.White)
        if (library.isEmpty()) {
            Text("Nothing imported yet.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            return
        }
        LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
            items(library.sortedByDescending { it.addedAt }, key = { it.id }) { asset ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onPicked(asset) }
                        .padding(8.dp)
                ) {
                    StickerWidget.StickerImage(
                        repo.fileOf(asset.file), asset.kind, DpSize(76.dp, 76.dp), playing = false, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

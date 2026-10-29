package com.customlauncher.ui.stickers

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.UiCommand
import com.customlauncher.container
import com.customlauncher.ui.LauncherViewModel

/** Where a new sticker comes from: gallery, any file (SAF), or the clipboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSourceSheet(vm: LauncherViewModel) {
    val context = LocalContext.current
    val close = { vm.stickerSourceSheetOpen = false }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importSticker(uri)
        close()
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSticker(uri)
        close()
    }

    ModalBottomSheet(onDismissRequest = close, containerColor = Color1E) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add sticker", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
            Text(
                "PNG/WebP with transparency, JPG, GIF, animated WebP, APNG, SVG or Lottie (.json). " +
                    "You can also share an image from any app → \"Add to home screen as sticker\".",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceTile(Icons.Filled.PhotoLibrary, "Gallery", Modifier.weight(1f)) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceTile(Icons.Filled.FolderOpen, "Files", Modifier.weight(1f)) {
                    files.launch(arrayOf("image/*", "application/json", "image/svg+xml", "application/octet-stream"))
                }
                SourceTile(Icons.Filled.ContentPaste, "Paste", Modifier.weight(1f)) {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val uri = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    if (uri != null) vm.importSticker(uri)
                    else context.container.bus.send(UiCommand.Message("The clipboard has no image"))
                    close()
                }
            }
            StickerLibraryGrid(vm, onPicked = { vm.addSticker(it); close() })
        }
    }
}

private val Color1E = androidx.compose.ui.graphics.Color(0xFF16181C)

@Composable
private fun SourceTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(label, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

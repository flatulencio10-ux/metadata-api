package com.customlauncher.widgets.media

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig

/** Now-playing card using MediaSessionManager (needs notification access). */
object MediaWidget : LauncherWidget {
    override val id = "media"
    override val name = "Media controller"
    override val description = "Control whatever is playing"
    override val icon = Icons.Filled.MusicNote
    override val defaultSize = DpSize(320.dp, 120.dp)

    private val showArt = ConfigField.Toggle("art", "Show album art", true)
    private val artBackground = ConfigField.Toggle("artBackground", "Album art as background", false)

    override val configSchema = listOf(showArt, artBackground)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val context = LocalContext.current
        val host = LocalWidgetHost.current
        val repo = context.container.mediaRepository
        LaunchedEffect(host.isActive) { if (host.isActive) repo.ensureAttached() }
        val hasAccess by repo.hasAccess.collectAsState()
        val state by repo.state.collectAsState()

        if (!hasAccess) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Media controls need notification access", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Grant") }
            }
            return
        }

        val s = state
        val art = s?.art?.asImageBitmap()
        val compact = size.width < 200.dp
        Box(Modifier.fillMaxSize()) {
            if (art != null && config.bool(artBackground)) {
                Image(art, null, Modifier.fillMaxSize().alpha(0.35f), contentScale = ContentScale.Crop)
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (art != null && config.bool(showArt) && !config.bool(artBackground) && !compact) {
                    Image(
                        art, null,
                        Modifier
                            .size(minOf(size.height - 16.dp, 96.dp))
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s?.title?.ifBlank { null } ?: "Nothing playing",
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!s?.artist.isNullOrBlank()) {
                        Text(s!!.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { if (s != null) repo.previous() else host.runAction(Action.MediaPrevious) }) {
                            Icon(Icons.Filled.SkipPrevious, "Previous")
                        }
                        IconButton(onClick = { if (s != null) repo.playPause() else host.runAction(Action.MediaPlayPause) }) {
                            Icon(if (s?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause")
                        }
                        IconButton(onClick = { if (s != null) repo.next() else host.runAction(Action.MediaNext) }) {
                            Icon(Icons.Filled.SkipNext, "Next")
                        }
                    }
                }
            }
        }
    }
}

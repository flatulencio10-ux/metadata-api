package com.customlauncher.widgets.stats

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.data.system.SystemStats
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.PollWhileActive
import com.customlauncher.widget.WidgetConfig

/** Battery, storage and RAM usage bars. */
object SystemStatsWidget : LauncherWidget {
    override val id = "system_stats"
    override val name = "System stats"
    override val description = "Battery, storage and memory usage"
    override val icon = Icons.Filled.Memory
    override val defaultSize = DpSize(160.dp, 160.dp)

    private val battery = ConfigField.Toggle("battery", "Show battery", true)
    private val storage = ConfigField.Toggle("storage", "Show storage", true)
    private val ram = ConfigField.Toggle("ram", "Show RAM", true)
    private val interval = ConfigField.Slider("interval", "Refresh every (seconds)", 2f, 60f, 5f)

    override val configSchema = listOf(battery, storage, ram, interval)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val context = LocalContext.current
        val repo = context.container.systemStats
        var stats by remember { mutableStateOf<SystemStats?>(null) }
        PollWhileActive(config.float(interval).toLong() * 1000) { stats = repo.snapshot() }

        val s = stats ?: return
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            if (config.bool(battery)) {
                StatRow(
                    if (s.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                    "Battery", "${s.batteryPercent}%" + if (s.charging) " ⚡" else "", s.batteryPercent / 100f,
                )
            }
            if (config.bool(storage)) {
                StatRow(
                    Icons.Filled.Storage, "Storage",
                    "${Formatter.formatShortFileSize(context, s.storageUsedBytes)} / ${Formatter.formatShortFileSize(context, s.storageTotalBytes)}",
                    s.storageUsedBytes.toFloat() / s.storageTotalBytes.coerceAtLeast(1),
                )
            }
            if (config.bool(ram)) {
                StatRow(
                    Icons.Filled.Memory, "RAM",
                    "${Formatter.formatShortFileSize(context, s.ramUsedBytes)} / ${Formatter.formatShortFileSize(context, s.ramTotalBytes)}",
                    s.ramUsedBytes.toFloat() / s.ramTotalBytes.coerceAtLeast(1),
                )
            }
        }
    }

    @Composable
    private fun StatRow(icon: ImageVector, label: String, value: String, fraction: Float) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Narrow widgets stack the label above the value; the value (not the label) ellipsizes.
            val narrow = maxWidth < 170.dp
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icon, null, Modifier.size(16.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    if (!narrow) {
                        Text(
                            value, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (narrow) {
                    Text(value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

package com.customlauncher.widgets.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.data.system.CalendarEvent
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.PollWhileActive
import com.customlauncher.widget.WidgetConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Upcoming events from all device calendars (CalendarContract). */
object AgendaWidget : LauncherWidget {
    override val id = "agenda"
    override val name = "Calendar agenda"
    override val description = "Upcoming events from your calendars"
    override val icon = Icons.Filled.Event
    override val defaultSize = DpSize(320.dp, 180.dp)

    private val days = ConfigField.Slider("days", "Days ahead", 1f, 30f, 7f, steps = 28)
    private val maxEvents = ConfigField.Slider("max", "Maximum events", 1f, 30f, 10f, steps = 28)
    private val showLocation = ConfigField.Toggle("location", "Show location", true)
    private val title = ConfigField.Text("title", "Title", default = "Agenda")

    override val configSchema = listOf(title, days, maxEvents, showLocation)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val repo = LocalContext.current.container.calendarRepository
        var events by remember { mutableStateOf<List<CalendarEvent>?>(null) }
        var permissionTick by remember { mutableIntStateOf(0) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionTick++
        }
        PollWhileActive(5 * 60_000L, config, permissionTick) {
            events = repo.upcoming(config.float(days).toInt(), config.float(maxEvents).toInt())
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                config.string(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { runCatching { repo.openCalendarApp() } }
                    .padding(bottom = 4.dp),
            )
            when {
                !repo.hasPermission() -> OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("Allow calendar access")
                }
                events == null -> Unit
                events!!.isEmpty() -> Text("No upcoming events", style = MaterialTheme.typography.bodySmall)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(events!!, key = { "${it.eventId}-${it.begin}" }) { e ->
                        EventRow(e, config.bool(showLocation)) { runCatching { repo.openEvent(e) } }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(e: CalendarEvent, showLocation: Boolean, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .background(Color(e.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append(whenText(e))
                    if (showLocation && e.location.isNotBlank()) append(" · ").append(e.location)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    private fun whenText(e: CalendarEvent): String {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(e.begin).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (start.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        }
        return if (e.allDay) "$day · all day" else "$day · ${start.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

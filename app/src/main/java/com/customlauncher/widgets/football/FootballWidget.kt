package com.customlauncher.widgets.football

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.customlauncher.container
import com.customlauncher.data.settings.SecureStore
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.rememberNow
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Football dashboard backed by football-data.org v4 (free tier).
 * Compact view: latest result + next fixture. Tap: full results list and standings.
 */
object FootballWidget : LauncherWidget {
    override val id = "football"
    override val name = "Football"
    override val description = "Latest result, next match and standings (football-data.org)"
    override val icon = Icons.Filled.SportsSoccer
    override val defaultSize = DpSize(320.dp, 220.dp)

    val competitionField = ConfigField.Choice("competition", "Competition", FREE_COMPETITIONS, default = "CL")
    private val teamField = ConfigField.Text("team", "Favorite team (optional)", hint = "Name, short name or TLA, e.g. Barcelona or FCB")
    private val apiKeyField = ConfigField.Secret(
        "apiKey", "football-data.org API key", SecureStore.FOOTBALL_API_KEY, hint = "Free key from football-data.org/client/register",
    )

    override val configSchema = listOf(
        apiKeyField,
        competitionField,
        teamField,
        ConfigField.Info(
            "info",
            "Get a free key at football-data.org (Register → the key arrives by e-mail). " +
                "Data refreshes every 15 minutes and is cached for offline use. Free tier: 10 requests/minute.",
        ),
    )

    override fun onAdded(context: Context, config: WidgetConfig) {
        FootballRefreshWorker.schedule(context)
    }

    @Composable
    private fun rememberFootball(config: WidgetConfig): Triple<FootballData?, RefreshResult?, () -> Unit> {
        val context = LocalContext.current
        val repo = context.container.footballRepository
        val host = LocalWidgetHost.current
        val code = config.string(competitionField)
        val data by remember(code) { repo.observe(code) }.collectAsState(initial = null)
        val keyVersion by context.container.secureStore.version.collectAsState()
        var result by remember { mutableStateOf<RefreshResult?>(null) }
        var forceTick by remember { mutableIntStateOf(0) }

        // Refresh when shown and stale (the repository skips fresh data and rate-limits).
        LaunchedEffect(host.isActive, code, keyVersion) {
            if (host.isActive) result = repo.refresh(code)
        }
        // Manual refresh button: bypasses the 15 minute freshness check (still rate limited).
        LaunchedEffect(forceTick) {
            if (forceTick > 0) result = repo.refresh(code, force = true)
        }
        return Triple(data, result) { forceTick++ }
    }

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val host = LocalWidgetHost.current
        val (data, result, refresh) = rememberFootball(config)
        val team = config.string(teamField)
        val compact = size.height < 160.dp

        Column(
            Modifier
                .fillMaxSize()
                .clickable(enabled = !host.isEditing) { host.openFullscreen() },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Header(data, config.string(competitionField), result, refresh)
            if (data == null) {
                StatusText(result, empty = true)
                return@Column
            }
            val recent = data.recent.filter { it.involves(team) }
            val upcoming = data.upcoming.filter { it.involves(team) }
            val last = recent.firstOrNull()
            val next = upcoming.firstOrNull()
            if (last != null) MatchRow(last, label = "Last result", crest = if (compact) 22.dp else 30.dp)
            if (next != null && (!compact || last == null)) MatchRow(next, label = "Next match", crest = if (compact) 22.dp else 30.dp)
            if (last == null && next == null) Text("No matches found", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    override fun FullscreenContent(config: WidgetConfig, size: DpSize) {
        val (data, result, refresh) = rememberFootball(config)
        val team = config.string(teamField)
        var tab by remember { mutableIntStateOf(0) }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Header(data, config.string(competitionField), result, refresh)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Results") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Fixtures") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Standings") })
            }
            if (data == null) {
                StatusText(result, empty = true)
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    0 -> items(data.recent.filter { it.involves(team) }.take(50)) { MatchRow(it, label = null, crest = 28.dp) }
                    1 -> items(data.upcoming.filter { it.involves(team) }.take(50)) { MatchRow(it, label = null, crest = 28.dp) }
                    else -> {
                        if (data.standings.isEmpty()) item { Text("No standings for this competition stage") }
                        data.standings.forEach { standing ->
                            item {
                                Text(
                                    standing.group?.replace('_', ' ') ?: standing.stage?.replace('_', ' ') ?: "Table",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            item { TableHeader() }
                            items(standing.table) { row -> TableRow(row, highlight = team.isNotBlank() && row.team.matches(team)) }
                        }
                    }
                }
            }
        }
    }

    private fun FdMatch.involves(team: String) = team.isBlank() || homeTeam.matches(team) || awayTeam.matches(team)

    @Composable
    private fun Header(data: FootballData?, code: String, result: RefreshResult?, onRefresh: () -> Unit) {
        val now = rememberNow(60_000)
        Row(verticalAlignment = Alignment.CenterVertically) {
            data?.competition?.emblem?.let {
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    data?.competition?.name ?: FREE_COMPETITIONS.firstOrNull { it.first == code }?.second ?: code,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val updated = data?.lastUpdated?.let {
                    "Updated " + DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS)
                } ?: "Never updated"
                val offline = (result as? RefreshResult.Failed)?.let { " · ${it.message}" } ?: ""
                Text(updated + offline, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
    }

    @Composable
    private fun StatusText(result: RefreshResult?, empty: Boolean) {
        val text = when (result) {
            RefreshResult.MissingKey -> "Add your free football-data.org API key in this widget's settings (Edit Mode → Configure)."
            is RefreshResult.Failed -> if (empty) "No data yet: ${result.message}" else result.message
            null -> "Loading…"
            else -> "No data"
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun MatchRow(match: FdMatch, label: String?, crest: Dp) {
        Column(Modifier.fillMaxWidth()) {
            if (label != null) Text("$label · ${formatDate(match.utcDate)}", style = MaterialTheme.typography.labelSmall)
            else Text(formatDate(match.utcDate), style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamCell(match.homeTeam, crest, Modifier.weight(1f), alignEnd = true)
                val finished = match.status == "FINISHED"
                val score = if (finished || match.status in setOf("IN_PLAY", "PAUSED", "LIVE")) {
                    "${match.score.fullTime.home ?: 0} – ${match.score.fullTime.away ?: 0}"
                } else formatTime(match.utcDate)
                Text(
                    score,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TeamCell(match.awayTeam, crest, Modifier.weight(1f), alignEnd = false)
            }
        }
    }

    @Composable
    private fun TeamCell(team: FdTeam, crest: Dp, modifier: Modifier, alignEnd: Boolean) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
            if (!alignEnd) Crest(team, crest)
            Text(
                team.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp).weight(1f, fill = false),
            )
            if (alignEnd) Crest(team, crest)
        }
    }

    @Composable
    private fun Crest(team: FdTeam, size: Dp) {
        AsyncImage(model = team.crest, contentDescription = team.displayName, modifier = Modifier.size(size))
    }

    @Composable
    private fun TableHeader() {
        Row(Modifier.fillMaxWidth()) {
            Text("#", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
            Text("Team", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            listOf("P", "W", "D", "L", "GD", "Pts").forEach {
                Text(it, Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
    }

    @Composable
    private fun TableRow(row: FdTableRow, highlight: Boolean) {
        val weight = if (highlight) FontWeight.Bold else FontWeight.Normal
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${row.position}", Modifier.width(28.dp), fontWeight = weight)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Crest(row.team, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(row.team.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = weight)
            }
            listOf(row.playedGames, row.won, row.draw, row.lost, row.goalDifference, row.points).forEach {
                Text("$it", Modifier.width(32.dp), textAlign = TextAlign.End, fontWeight = weight)
            }
        }
    }

    private fun parse(utc: String) = runCatching { OffsetDateTime.parse(utc).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

    private fun formatDate(utc: String): String =
        parse(utc)?.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")) ?: utc

    private fun formatTime(utc: String): String =
        parse(utc)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "vs"
}

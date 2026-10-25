package com.customlauncher.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.ui.graphics.vector.ImageVector
import com.customlauncher.AppContainer
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.data.apps.AppInfo
import com.customlauncher.data.apps.AppShortcut
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** What a result shows as its icon. */
sealed interface ResultIcon {
    data class App(val key: String) : ResultIcon
    data class Vector(val image: ImageVector) : ResultIcon
}

data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: ResultIcon,
    /** Higher = shown first. Calculator/unit answers use 1000, web fallback 0. */
    val score: Int,
    val onRun: () -> Unit,
)

/** Everything a provider may need, captured per query. */
class SearchContext(
    val context: Context,
    val container: AppContainer,
    val profile: LayoutProfile,
    val apps: List<AppInfo>,
    /** Runs an action and closes the search overlay. */
    val run: (Action) -> Unit,
    val close: () -> Unit,
)

/**
 * KRunner-style search source. To add one: implement this interface and add it to
 * [SearchEngine.providers].
 */
interface SearchProvider {
    val id: String
    suspend fun search(query: String, ctx: SearchContext): List<SearchResult>
}

class SearchEngine(private val providers: List<SearchProvider> = DEFAULT_PROVIDERS) {

    /** Queries all providers in parallel and merges results by score. */
    suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return coroutineScope {
            providers.map { p -> async { runCatching { p.search(q, ctx) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
                .sortedByDescending { it.score }
        }
    }

    companion object {
        val DEFAULT_PROVIDERS: List<SearchProvider> = listOf(
            CalculatorProvider, UnitProvider, AppsProvider, LauncherActionsProvider,
            ShortcutsProvider, ContactsProvider, SettingsProvider, WebProvider,
        )

        /** 0 = no match; higher = better (prefix > word prefix > substring > subsequence). */
        fun matchScore(text: String, query: String): Int {
            val t = text.lowercase()
            val q = query.lowercase()
            return when {
                t == q -> 500
                t.startsWith(q) -> 400
                t.split(' ', '-', '_').any { it.startsWith(q) } -> 300
                t.contains(q) -> 200
                isSubsequence(q, t) -> 100
                else -> 0
            }
        }

        private fun isSubsequence(q: String, t: String): Boolean {
            var i = 0
            for (c in t) if (i < q.length && c == q[i]) i++
            return i == q.length && q.length >= 2
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("result", text))
}

object CalculatorProvider : SearchProvider {
    override val id = "calculator"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        if (!ctx.profile.search.calculator) return emptyList()
        val value = Calculator.evaluate(query) ?: return emptyList()
        val text = Calculator.format(value)
        return listOf(SearchResult("calc", "= $text", "$query · tap to copy", ResultIcon.Vector(Icons.Filled.Calculate), 1000) {
            ctx.context.copyToClipboard(text)
        })
    }
}

object UnitProvider : SearchProvider {
    override val id = "units"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        if (!ctx.profile.search.unitConversion) return emptyList()
        val r = UnitConverter.convert(query) ?: return emptyList()
        val text = "${Calculator.format(r.converted)} ${r.to}"
        return listOf(
            SearchResult("unit", text, "${Calculator.format(r.value)} ${r.from} · tap to copy", ResultIcon.Vector(Icons.Filled.Straighten), 1000) {
                ctx.context.copyToClipboard(text)
            }
        )
    }
}

object AppsProvider : SearchProvider {
    override val id = "apps"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> =
        ctx.apps.filter { !it.hidden }.mapNotNull { app ->
            val score = maxOf(SearchEngine.matchScore(app.label, query), SearchEngine.matchScore(app.originalLabel, query) - 10)
            if (score == 0) null
            else SearchResult("app:${app.key}", app.label, app.category, ResultIcon.App(app.key), score + 50) {
                ctx.run(Action.OpenApp(app.key))
            }
        }.sortedByDescending { it.score }.take(8)
}

/** Launcher actions by name: "lock", "flashlight", "edit", activity names… */
object LauncherActionsProvider : SearchProvider {
    override val id = "actions"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        val actions = ActionCatalog.simpleActions.filter { it != Action.None } +
            ctx.profile.activities.map { Action.SwitchActivity(it.id) }
        return actions.mapNotNull { action ->
            val label = ActionCatalog.label(action, ctx.profile)
            val score = SearchEngine.matchScore(label, query)
            if (score < 200) null
            else SearchResult("action:$label", label, "Launcher action", ResultIcon.Vector(ActionCatalog.icon(action)), score - 100) {
                ctx.run(action)
            }
        }.take(4)
    }
}

object ShortcutsProvider : SearchProvider {
    override val id = "shortcuts"
    @Volatile private var cache: Pair<Long, List<AppShortcut>>? = null

    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        if (!ctx.profile.search.searchShortcuts || query.length < 2) return emptyList()
        val now = System.currentTimeMillis()
        val all = cache?.takeIf { now - it.first < 60_000 }?.second
            ?: ctx.container.shortcutRepository.shortcutsFor(null).also { cache = now to it }
        return all.mapNotNull { s ->
            val score = SearchEngine.matchScore(s.label, query)
            if (score < 200) null
            else {
                val appKey = ctx.apps.firstOrNull { it.packageName == s.packageName }?.key
                SearchResult(
                    "shortcut:${s.packageName}/${s.id}", s.label,
                    ctx.apps.firstOrNull { it.packageName == s.packageName }?.label ?: s.packageName,
                    if (appKey != null) ResultIcon.App(appKey) else ResultIcon.Vector(Icons.Filled.Settings),
                    score - 50,
                ) { ctx.run(Action.OpenShortcut(s.packageName, s.id, s.label)) }
            }
        }.take(5)
    }
}

object ContactsProvider : SearchProvider {
    override val id = "contacts"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        if (!ctx.profile.search.searchContacts || query.length < 2) return emptyList()
        val repo = ctx.container.contactsRepository
        return repo.search(query).map { c ->
            SearchResult("contact:${c.id}", c.name, "Contact", ResultIcon.Vector(Icons.Filled.Person), 150 + SearchEngine.matchScore(c.name, query) / 10) {
                repo.open(c); ctx.close()
            }
        }
    }
}

object SettingsProvider : SearchProvider {
    override val id = "settings"
    private val entries = listOf(
        "Settings" to Settings.ACTION_SETTINGS,
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Display & brightness" to Settings.ACTION_DISPLAY_SETTINGS,
        "Sound & volume" to Settings.ACTION_SOUND_SETTINGS,
        "Battery saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Security" to Settings.ACTION_SECURITY_SETTINGS,
        "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "Date & time" to Settings.ACTION_DATE_SETTINGS,
        "Language" to Settings.ACTION_LOCALE_SETTINGS,
        "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "Network & internet" to Settings.ACTION_WIRELESS_SETTINGS,
        "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "About phone" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        "NFC" to Settings.ACTION_NFC_SETTINGS,
        "Default home app" to Settings.ACTION_HOME_SETTINGS,
        "Notification access" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
    )

    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        if (!ctx.profile.search.searchSettings) return emptyList()
        return entries.mapNotNull { (label, action) ->
            val score = SearchEngine.matchScore(label, query)
            if (score < 200) null
            else SearchResult("settings:$action", label, "System settings", ResultIcon.Vector(Icons.Filled.Settings), score - 150) {
                runCatching { ctx.context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                ctx.close()
            }
        }
    }
}

object WebProvider : SearchProvider {
    override val id = "web"
    override suspend fun search(query: String, ctx: SearchContext): List<SearchResult> {
        val template = ctx.profile.search.webSearchUrl.ifBlank { "https://duckduckgo.com/?q=%s" }
        val url = template.replace("%s", Uri.encode(query))
        return listOf(SearchResult("web", "Search the web for “$query”", Uri.parse(url).host ?: url, ResultIcon.Vector(Icons.Filled.Language), 0) {
            ctx.run(Action.OpenUrl(url))
        })
    }
}

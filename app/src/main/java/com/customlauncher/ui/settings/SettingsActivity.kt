package com.customlauncher.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.customlauncher.container
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.data.apps.AppRepository
import com.customlauncher.ui.common.AppCatalog
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.combine

/** Settings screens. Every change writes straight into the layout profile. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SettingsRoot(onExit = { finish() }) }
    }
}

enum class SettingsRoute(val title: String, val icon: ImageVector, val summary: String) {
    ACTIVITIES("Activities", Icons.Filled.SwapHoriz, "Home setups, wallpapers, auto-switching"),
    THEME("Theme", Icons.Filled.Palette, "Colors, fonts, icon shape, icon packs"),
    GESTURES("Gestures & hot corners", Icons.Filled.Gesture, "Swipes, pinches, letters, corners, edges, volume keys"),
    PANELS("Panels", Icons.Filled.Dashboard, "Docks and bars on any screen edge"),
    DRAWER("App drawer", Icons.Filled.Apps, "Layout, sorting, hidden apps"),
    SEARCH("Search", Icons.Filled.Search, "Providers and web search engine"),
    BACKUP("Backup & profiles", Icons.Filled.Save, "Export / import the whole setup as JSON"),
    PERMISSIONS("Permissions", Icons.Filled.Security, "Default home, accessibility, notifications"),
}

/** Gives each settings screen the current profile and an edit function. */
class SettingsScope(val profile: LayoutProfile, val edit: ((LayoutProfile) -> LayoutProfile) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(onExit: () -> Unit) {
    val container = androidx.compose.ui.platform.LocalContext.current.container
    val repo = container.profileRepository
    val profile = repo.profile.collectAsStateWithLifecycle().value ?: return
    val apps by remember {
        combine(container.appRepository.rawApps, repo.profile) { raw, p -> AppRepository.applyOverrides(raw, p?.appOverrides.orEmpty()) }
    }.collectAsStateWithLifecycle(emptyList())
    var route by rememberSaveable { mutableStateOf<SettingsRoute?>(null) }
    BackHandler(enabled = route != null) { route = null }

    val scope = SettingsScope(profile) { transform -> repo.edit(transform) }

    LauncherTheme(profile.theme) {
        CompositionLocalProvider(LocalAppCatalog provides remember(apps) { AppCatalog(apps) }) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(route?.title ?: "Launcher settings") },
                        navigationIcon = {
                            IconButton(onClick = { if (route != null) route = null else onExit() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    when (route) {
                        null -> LazyColumn {
                            items(SettingsRoute.entries) { r ->
                                ListItem(
                                    headlineContent = { Text(r.title) },
                                    supportingContent = { Text(r.summary) },
                                    leadingContent = { Icon(r.icon, null) },
                                    modifier = Modifier.clickable { route = r },
                                )
                            }
                        }
                        SettingsRoute.ACTIVITIES -> ActivitiesScreen(scope)
                        SettingsRoute.THEME -> ThemeScreen(scope)
                        SettingsRoute.GESTURES -> GesturesScreen(scope)
                        SettingsRoute.PANELS -> PanelsScreen(scope)
                        SettingsRoute.DRAWER -> DrawerSettingsScreen(scope)
                        SettingsRoute.SEARCH -> SearchSettingsScreen(scope)
                        SettingsRoute.BACKUP -> BackupScreen(scope)
                        SettingsRoute.PERMISSIONS -> com.customlauncher.ui.onboarding.OnboardingScreen(onFinish = { route = null })
                    }
                }
            }
        }
    }
}

/** Standard padded, scrolling settings page. */
@Composable
fun SettingsPage(content: LazyListScope.() -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

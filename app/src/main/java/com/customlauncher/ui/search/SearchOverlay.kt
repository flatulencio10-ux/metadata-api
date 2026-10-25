package com.customlauncher.ui.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.search.ResultIcon
import com.customlauncher.search.SearchContext
import com.customlauncher.search.SearchEngine
import com.customlauncher.search.SearchResult
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.LocalAppCatalog
import kotlinx.coroutines.delay

/** KRunner-style universal search overlay. Enter runs the top result. */
@Composable
fun SearchOverlay(profile: LayoutProfile, vm: LauncherViewModel) {
    val context = LocalContext.current
    val apps = LocalAppCatalog.current.apps
    val engine = remember { SearchEngine() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    val close = { vm.searchOpen = false }
    val ctx = remember(profile, apps) {
        SearchContext(
            context = context,
            container = context.container,
            profile = profile,
            apps = apps,
            run = { action: Action -> vm.run(action); vm.searchOpen = false },
            close = { vm.searchOpen = false },
        )
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, ctx, permissionTick) {
        delay(120) // debounce typing
        results = engine.search(query, ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Apps, contacts, settings, 2+2*3, 10 km to mi…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.onRun?.invoke() ?: close() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        if (profile.search.searchContacts && !context.container.contactsRepository.hasPermission() && query.length >= 2) {
            TextButton(onClick = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                Text("Allow contacts access to search contacts")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.title) },
                    supportingContent = { if (r.subtitle.isNotEmpty()) Text(r.subtitle) },
                    leadingContent = {
                        when (val icon = r.icon) {
                            is ResultIcon.App -> AppIcon(icon.key, 40.dp)
                            is ResultIcon.Vector -> Icon(icon.image, null)
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = Color.White,
                        supportingColor = Color.White.copy(alpha = 0.7f),
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.clickable { r.onRun() },
                )
            }
        }
    }
}

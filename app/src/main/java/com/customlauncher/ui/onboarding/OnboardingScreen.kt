package com.customlauncher.ui.onboarding

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.customlauncher.services.LauncherAccessibilityService
import com.customlauncher.services.MediaNotificationListener

/**
 * First run: make this the default home app, then enable the accessibility service and
 * notification access. Every step is optional and deep-links to the right settings page.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    // Re-check states whenever the user comes back from a settings screen.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val roleManager = context.getSystemService(RoleManager::class.java)
    val isHome = remember(refresh) { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    val accessibility = remember(refresh) { LauncherAccessibilityService.isEnabled(context) }
    val notifications = remember(refresh) { MediaNotificationListener.isEnabled(context) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Everything here is configurable. Long-press empty space to enter Edit Mode, swipe up for apps, swipe down to search.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground,
        )
        Step(
            title = "Set as default home app",
            body = "Needed for the Home button and app shortcuts.",
            done = isHome,
            button = "Set default",
        ) {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        }
        Step(
            title = "Accessibility service",
            body = "Lets gestures lock the screen, open recents / notifications, and use volume-key long presses. No screen content is read.",
            done = accessibility,
            button = "Open settings",
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        Step(
            title = "Notification access",
            body = "Used only by the media widget to control what's playing.",
            done = notifications,
            button = "Open settings",
        ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("Skip") }
            Button(onClick = onFinish) { Text("Done") }
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!done) OutlinedButton(onClick = onClick) { Text(button) } else Text("Done ✓", style = MaterialTheme.typography.labelLarge)
        }
    }
}

# Android Custom Launcher

A highly customizable Android home screen inspired by KDE Plasma, written in Kotlin and Jetpack Compose.
Nothing is hard-coded: layout, colors, fonts, icons, gestures, panels and widgets are all user configuration,
stored as one JSON **layout profile** that you can export, share and import.

- minSdk 29, single `:app` module, manual DI (`AppContainer`), no reflection-based frameworks.
- The core is built to be extended: a new widget is **one file + one registry line**.

## Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and choose **Custom Launcher** (or follow the first-run screen).
Every push also builds the APK in GitHub Actions (`.github/workflows/android.yml`); download it from the run's artifacts.

## Features

| Area | What you get |
| --- | --- |
| **Edit Mode** | Long-press empty space (or pinch in). Free placement with fine snapping (4/8/16 dp) or snapping off for free positioning; drag to move, corner handles to resize; bring to front / send to back; per-widget opacity, corner radius, background tint, background blur (frosted glass over launcher wallpapers, Android 12+) and padding; duplicate, delete, move between pages. |
| **Panels** | Any number of panels on any edge (top/bottom/left/right). Thickness, floating or edge-attached, margin, corner radius, auto-hide, opacity, background, reserve space. Holds app icons, widgets, action buttons and fixed/flexible spacers. |
| **Activities** | Several complete home setups ("School", "Gaming", "Night"…), each with its own wallpaper, pages and panels. Switch manually, by action/gesture, or automatically by time of day, WiFi network (SSID) or charging state. |
| **Pages** | Unlimited pages; each has its own wallpaper (or inherits the activity's) and transition: slide, cube, fade, zoom, stack. |
| **Universal search** | KRunner-style: apps, app shortcuts, contacts, system settings, launcher actions, inline calculator (`2+2*3`, `sqrt(2)^2`), unit conversion (`10 km to mi`, `100 f to c`, `1.5 gb to mb`), configurable web-search fallback. Enter runs the top hit. |
| **Gestures** | Swipe up/down/left/right, two-finger swipes, double tap, pinch in/out, **draw-a-letter** (C E G L M N O S U V W Z), 4 hot corners × tap/long-press, 4 edge swipes, volume-up/down long press. Every one maps to any action. |
| **Actions** | Open app, open app shortcut, open a widget full screen, switch/next activity, open search/drawer/settings, Edit Mode, lock, recents, notification shade, quick settings, flashlight, media play/pause/next/previous, next/previous page, open URL, and **macros** (a sequence of actions with a delay). |
| **Theme engine** | Primary / accent / surface / background / text colors (HSV + hex picker), Material You toggle, light/dark/system, 4 bundled fonts (Inter, Lora, JetBrains Mono, Comfortaa) plus importing any .ttf/.otf, icon shapes (circle, squircle, square, teardrop, none), ADW/Nova icon packs (`appfilter.xml`), per-app icon from the gallery, icon size, label size/visibility. |
| **App drawer** | Grid / list / categories (from the Play category in `ApplicationInfo.category`), search, 4 sort orders, hide/unhide, rename, custom icons, custom folders, app shortcuts in the long-press menu, add to home / panel. |
| **Profiles** | Export/import the whole setup as `.json` (Settings → Backup & profiles). API keys are never exported. |
| **Performance** | Widgets get `isActive = false` when their page is off screen, an overlay covers them, or the launcher is paused; tickers and polling stop. Page transitions are drawn in the graphics layer, so scrolling does not recompose. |

### Starter widgets

Clock (digital / analog / word clock, custom `java.time` format) · System stats (battery, storage, RAM) ·
Calendar agenda (CalendarContract) · Sticky note (type directly on the home screen, persisted) ·
Media controller (MediaSession via notification access) · App folder grid · Single app icon ·
**Football** dashboard (football-data.org).

## Permissions & first run

The first-run screen deep-links to each step; all are optional:

1. **Default home app**: Home button handling and app shortcuts.
2. **Accessibility service** (`LauncherAccessibilityService`): lock screen, recents, notification shade,
   quick settings and volume-key long presses. It never reads window content.
3. **Notification access** (`MediaNotificationListener`): needed only for the media widget.

Calendar, contacts and location (WiFi SSID for auto-switching) are requested at runtime, only when used.

## Football widget: getting an API key

1. Register for free at <https://www.football-data.org/client/register>; the key arrives by e-mail.
2. Edit Mode → tap the football widget → **Configure** → paste the key → **Save**.
   The key is stored with `EncryptedSharedPreferences`, never in the profile JSON.
3. Pick a competition (CL default; PL, PD, SA, BL1, FL1) and optionally a favourite team (name, short name or TLA).

Data is cached in Room and refreshed every 15 minutes by WorkManager. Requests go through a 10 requests/minute
limiter that backs off on HTTP 429. The widget shows "Updated N min ago" and keeps cached data when offline.
Tap the widget for results, fixtures and standings. Crests load with Coil (SVG supported).

## Architecture

```
core/model        Serializable domain model: LayoutProfile → ActivityProfile → Page/Panel → WidgetInstance, Action, Trigger…
core/json         Shared kotlinx.serialization config (ignores unknown keys, so old builds import newer files)
data/             Repositories: profile (DataStore, one JSON file), apps/icons/icon packs/shortcuts, system sources, Room cache
widget/           Plugin API: LauncherWidget, ConfigField schema, WidgetConfig, WidgetHost, WidgetRegistry, auto config UI
widgets/          The starter widgets, one package each
actions/          ActionExecutor (system actions) + LauncherBus (UI commands) + ActionCatalog (labels/icons)
gestures/         Home-screen gesture engine, letter recogniser, gesture canvas
search/           SearchEngine + pluggable SearchProvider list, Calculator, UnitConverter
activities/       Automatic activity switching (time / WiFi / charging)
services/         Accessibility service and notification listener
ui/               Compose UI: home, edit mode, drawer, search, settings, onboarding, theme
```

Data flow: the UI reads `ProfileRepository.profile` (a `StateFlow<LayoutProfile?>`) and writes through
`ProfileRepository.edit { profile -> newProfile }`. DataStore serializes the writes, so edits from widgets, Edit Mode and
settings never race. The profile is normalized on every write (always ≥1 activity, ≥1 page, valid current activity).

## Adding a new widget

1. Create `app/src/main/java/com/customlauncher/widgets/<name>/<Name>Widget.kt`:

```kotlin
package com.customlauncher.widgets.counter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.fitTextSize

object CounterWidget : LauncherWidget {
    override val id = "counter"                 // stored in layouts: never change it
    override val name = "Counter"
    override val description = "Tap to count"
    override val icon = Icons.Filled.Add
    override val defaultSize = DpSize(160.dp, 120.dp)

    // Declaring settings is enough: the config screen is generated from this list.
    private val label = ConfigField.Text("label", "Label", default = "Coffees")
    private val step = ConfigField.Slider("step", "Step", 1f, 10f, 1f, steps = 8)
    override val configSchema = listOf(label, step)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val host = LocalWidgetHost.current       // instanceId, isActive, updateConfig, runAction…
        val count = config.int("count")
        Box(
            Modifier
                .fillMaxSize()
                .clickable(enabled = !host.isEditing) {
                    // Persist state in the widget's own config.
                    host.updateConfig(config.with("count", count + config.float(step).toInt()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("${config.string(label)}: $count", fontSize = fitTextSize(size, 12))
        }
    }
}
```

2. Register it in `widget/WidgetRegistry.kt`:

```kotlin
val all: List<LauncherWidget> = listOf(
    ClockWidget,
    // …
    CounterWidget,
)
```

That's it: the widget shows up in Edit Mode → **Widget**, can be placed on pages and in panels, styled, configured,
opened full screen and exported with profiles.

Optional hooks on `LauncherWidget`:

- `ConfigScreen(config, onChange)`: replace the generated settings UI.
- `FullscreenContent(config, size)`: a richer view for "open widget full screen" (see `FootballWidget`).
- `onAdded(context, config)`: e.g. schedule WorkManager jobs.
- `minSize`: smallest size allowed by the resize handles.

Guidelines: read `LocalWidgetHost.current.isActive` (or use `rememberNow` / `PollWhileActive` from
`widget/WidgetUtils.kt`) so the widget pauses off screen; put secrets in a `ConfigField.Secret` (encrypted storage), never in config;
cache network data in the `cached_response` Room table (`CacheDao`) like the football widget does.

Similar extension points: new **actions** (`core/model/Action.kt` + `ActionExecutor` + `ActionCatalog`),
**search providers** (implement `SearchProvider`, add it to `SearchEngine.DEFAULT_PROVIDERS`),
**letters** (add a polyline to `LetterRecognizer.templates`).

## Tests

`./gradlew testDebugUnitTest` runs JVM tests for the calculator, unit converter, letter recogniser, auto-switch rules
and profile JSON round-trips.

## Licenses

Bundled fonts (Inter, Lora, JetBrains Mono, Comfortaa) are under the SIL Open Font License; see `licenses/fonts/`.

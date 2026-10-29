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
| **Edit Mode** | Long-press an object, or long-press empty space → *Edit home* (or pinch in). Free placement with fine snapping (4/8/16 dp) or snapping off for free positioning; drag to move, corner handles to resize; bring to front / send to back; per-widget opacity, corner radius, background tint, background blur (frosted glass over launcher wallpapers, Android 12+) and padding; duplicate, delete, move between pages. |
| **Panels** | Any number of panels on any edge (top/bottom/left/right). Thickness, floating or edge-attached, margin, corner radius, auto-hide, opacity, background, reserve space. Holds app icons, widgets, action buttons and fixed/flexible spacers. |
| **Activities** | Several complete home setups ("School", "Gaming", "Night"…), each with its own wallpaper, pages and panels. Switch manually, by action/gesture, or automatically by time of day, WiFi network (SSID) or charging state. |
| **Pages** | Unlimited pages; each has its own wallpaper (or inherits the activity's) and transition: slide, cube, fade, zoom, stack. |
| **Universal search** | KRunner-style: apps, app shortcuts, contacts, system settings, launcher actions, inline calculator (`2+2*3`, `sqrt(2)^2`), unit conversion (`10 km to mi`, `100 f to c`, `1.5 gb to mb`), configurable web-search fallback. Enter runs the top hit. |
| **Gestures** | Swipe up/down/left/right, two-finger swipes, double tap, pinch in/out, **draw-a-letter** (C E G L M N O S U V W Z), 4 hot corners × tap/long-press, 4 edge swipes, volume-up/down long press. Every one maps to any action. |
| **Actions** | Open app, open app shortcut, open a widget full screen, switch/next activity, open search/drawer/settings, Edit Mode, lock, recents, notification shade, quick settings, flashlight, media play/pause/next/previous, next/previous page, open URL, and **macros** (a sequence of actions with a delay). |
| **Theme engine** | Primary / accent / surface / background / text colors (HSV + hex picker), Material You toggle, light/dark/system, 4 bundled fonts (Inter, Lora, JetBrains Mono, Comfortaa) plus importing any .ttf/.otf, icon shapes (circle, squircle, square, teardrop, none), ADW/Nova icon packs (`appfilter.xml`), per-app icon from the gallery, icon size, label size/visibility. |
| **App drawer** | Grid / list / categories (from the Play category in `ApplicationInfo.category`), search, 4 sort orders, hide/unhide, rename, custom icons, custom folders, app shortcuts in the long-press menu, add to home / panel. |
| **Objects** | Physics, no-code behaviours, states, variables, keyframe clips and JavaScript per object; interactive presets (see below). |
| **Desktop** | Start menu, taskbar with recent apps, system tray, floating widget windows, free-form app windows, context menu, copy/paste. |
| **Profiles** | Export/import the whole setup as `.json` (Settings → Backup & profiles). API keys are never exported. |
| **Performance** | Widgets get `isActive = false` when their page is off screen, an overlay covers them, or the launcher is paused; tickers and polling stop. Page transitions are drawn in the graphics layer, so scrolling does not recompose. |

### Starter widgets

Clock (digital / analog / word clock, custom `java.time` format) · System stats (battery, storage, RAM) ·
Calendar agenda (CalendarContract) · Sticky note (type directly on the home screen, persisted) ·
Media controller (MediaSession via notification access) · App folder grid · Single app icon ·
**Football** dashboard (football-data.org).

## Stickers

Stickers are free-form images on the home screen: no card, border or clipping. Transparent
pixels stay transparent, and a tap on a transparent pixel goes through to whatever is underneath.

**Adding a sticker**

- Edit Mode → **Widget** → **Sticker**, then choose **Gallery**, **Files** or **Paste** (an image you copied).
- From any app's share sheet (Chrome, Gallery…): **Add to home screen as sticker**.
- Edit Mode → **Widget** → **My stickers** reuses anything you imported before. Long-press a sticker
  there to favourite, rename or delete it.

Supported formats: PNG/WebP with transparency, JPG, animated GIF, animated WebP, APNG (animates
where Android's decoder supports it), SVG and Lottie `.json`. Files are copied into the app's storage,
so deleting the original doesn't break the sticker.

**Moving and arranging** (Edit Mode)

- One finger moves, two fingers pinch to scale and twist to rotate. The corner handles scale; the
  round handle above rotates, snapping to 15° steps with a haptic tick.
- Stickers always place freely, even when the grid is on. Pink guide lines appear at the page
  centre, page edges and other widgets' edges.
- The floating toolbar does flip horizontally/vertically, duplicate, bring to front / send to back,
  layer (**above** or **below** the panels, e.g. a cloud behind the dock), **pin across all pages**,
  lock, edit and delete.

**Removing backgrounds and cutting out** (toolbar → Edit)

- **Cut → Remove BG**: on-device subject segmentation with ML Kit (Google Play services downloads the
  model the first time). Hold **Hold: before/after** to compare with the original.
- **Lasso**: draw around the part to keep. **Eraser** / **Restore**: brush away or paint back pixels,
  with a size slider. Undo/redo at the top.
- **Shape**: crop to a circle, rounded rectangle, star, heart or blob.
- **Effects**: outline (sticker border), drop shadow, glow, tint, opacity and blend mode. Outline,
  shadow and glow apply to still images. Blend modes mix with widgets on the same page, not with the
  system wallpaper.

**Animations** (Edit → Animate; any slot can stay off)

- **Entrance**: fade, fly in from any edge, zoom, pop, spin, bounce drop, wipe, slide + fade,
  typewriter reveal. It plays when the page is shown, when you unlock, or when the launcher opens.
- **Idle loop**: float/bob, wiggle, pulse, rotate, sway (choose the pivot), shake, breathe, or drift
  along a path you draw with your finger.
- **On tap**: jump, spin, squash & stretch, shake, explode into its own pixels and re-form, or play
  a GIF once. A tap can also run any action (open an app, a macro…).
- Each slot has duration, delay, easing (linear, ease, spring, bounce, overshoot) and repeat. Use
  **Preview** to try it.
- **Parallax**: depth makes the sticker drift slightly while swiping pages; **Follow device tilt**
  adds a gyroscope effect.
- Idle loops stop off screen, with the screen off, and in battery saver or below 15% battery
  (Settings → Performance can override this).

**Sharing**: Settings → Backup & profiles → **Export everything (.zip)** writes `layout.json` plus
`stickers/`, wallpapers, custom icons and fonts. Import accepts the `.zip` or a plain `.json`.

## Objects: a computer in your phone

Every thing on a page — widget, sticker, preset toy — is a **node** (object). Nodes can be
plain decorations, or live objects with physics, rules, animations and code.

### The object system

| Layer | What it is | Where to edit it |
| --- | --- | --- |
| **Transform** | Position, size, rotation, scale, pivot, flip, lock, hidden, layer (below / above panels), group | Edit Mode → tap → inspector **Transform** tab; **Layers** button for the layer list |
| **Look** | Opacity, card style, blur, tint, sticker editor | **Look** tab |
| **Physics** | A rigid body (JBox2D): dynamic, static or kinematic; mass, friction, bounciness, damping, gravity scale, collider (auto hull from the image, circle, box, custom polygon), pin to the page (spinners, pendulums) | **Physics** tab, plus page settings for the world (gravity, tilt gravity, walls) |
| **Speed visuals** | Motion blur by rpm, sprite-sheet "zoetrope" frames, stretch when thrown, trails, speed lines, sparks on collision, haptic tick per turn | **Physics** tab → Speed-reactive visuals |
| **Behaviours** | No-code rules: *When ⟨trigger⟩ if ⟨conditions⟩ do ⟨actions⟩* | **Behaviors** tab |
| **States** | A state machine (idle, tapped, spinning, dragging + your own), each with its own image, animation, sound and rules | **Behaviors** tab → States |
| **Variables** | Numbers per object or global, saved across restarts | **Behaviors** tab → Variables |
| **Animations** | Entrance / idle / tap effects, parallax, and named **keyframe clips** | **Animation** tab |
| **Script** | JavaScript hooks for anything the rules can't express | **Script** tab |

Objects live on a page (and slide with it), on the **pinned** layer (fixed while pages swipe),
or in a **floating window**. Infinite pages (page settings → *Infinite canvas*) pan and zoom.

Presets under **Add → Objects** (fidget spinner, bouncy ball, cloud rider, pendulum, stress
blob, lever, switch, knob, spawn button) are ordinary objects built from these parts, so you
can open their Behaviors tab to see how they work, or apply one as a template to your own image.

### Behaviour editor guide

1. Edit Mode → tap an object → **Behaviors** → **Add behaviour**.
2. **When** — pick a trigger: tapped, double-tapped, long-pressed, drag starts/ends, thrown
   faster than N dp/s, spin goes above/below N rpm, hits anything / a specific object, enters or
   leaves a zone, page shown, screen unlocks, time of day, battery below N%, notification from an
   app, music starts/pauses, phone shaken, phone tilted, every N seconds, random chance.
3. **If** (optional) — conditions that must all hold: a variable compared with a number
   (`taps ≥ 10`, `global.coins < 3`), the current state, or a % chance.
4. **Do** — a list of actions, run in order: play a reaction or keyframe clip, swap image, go to
   state, play a sprite sheet, push / spin, move to a point, fly to an offset, follow the finger,
   follow a path, orbit another object, particle burst, sound, haptic pattern, speech bubble, an
   action on another object, set / add to a variable, any launcher action (open app, macro…),
   physics on/off, wait, one random action, spawn a preset.
5. Each rule shows as a sentence ("When tapped · if taps ≥ 10 · do say "Ten!""); toggle it off
   without deleting it. The chips above the list switch between the object's rules and each
   state's rules.

**States**: add states in the same tab. The automatic ones are entered for you — *tapped*
briefly after a tap, *dragging* while held, *spinning* above 60 rpm. A state's rules only run
while it is active, and its transition time cross-fades the image.

**Keyframe clips**: **Animation** tab → *Keyframe timeline*. Make a clip, tap a lane to add a
key at the playhead (move, rotation, scale, opacity, tint, skew, blur), drag the ruler to scrub
(the object previews on the page), pick an easing per segment or drag the bezier handles.
**Record path** lets you drag anywhere on the page and turns your finger path into keys. Play
clips from a rule (*play clip*) or a script (`this.play("name")`).

### Scripting API reference

Scripts are JavaScript (ES6 subset, Mozilla Rhino), one per object, in the **Script** tab.
Define any of these hook functions; inside them `this` is the object.

| Hook | Called when |
| --- | --- |
| `onTap(e)`, `onDoubleTap(e)`, `onLongPress(e)` | the object is touched (`e.type`) |
| `onDragStart(e)`, `onDragEnd(e)` | a drag starts / ends (`e.speed` in dp/s) |
| `onSpin(rpm)` | ~15×/s while it spins, plus once when it stops |
| `onCollide(otherId, impulse)` | a physics collision (`otherId` is null for walls) |
| `onTick(dt)` | every frame, up to 30×/s (`dt` in seconds) |
| `onState(name)` | it entered a state |
| `onNotification(packageName)` | any app posts a notification (needs notification access) |
| `onMessage(name, data, fromId)` | another script called `launcher.broadcast` |

| `this.` | |
| --- | --- |
| `x`, `y` | position in dp (read/write; writing moves the body) |
| `rotation`, `scale` | degrees / factor (read/write) |
| `rpm`, `vx`, `vy` | spin speed and velocity (read; `vx`/`vy` writable for physics bodies) |
| `state` | current state name (write to change state) |
| `id`, `vars` | object id; per-object numeric variables (`this.vars.taps`) |
| `applyImpulse(dx, dy)` | push (dp/s); moves non-physics objects with a springy hop |
| `spin(rpm)` | add spin |
| `setImage(name)` | swap to a sticker from your library (by name or file) |
| `play(clip)` | play a keyframe clip |
| `emit(count, "#RRGGBB")` | particle burst |
| `haptic("tick" \| "click" \| "heavy" \| "double" \| "long")` | vibrate |
| `say(text, ms)` | speech bubble |
| `sound(src)` | `"tone:NAME"` or `"file:..."` |
| `moveTo(x, y, ms)` | animate to a point |

Globals: `launcher.openApp(packageName)`, `launcher.vars` (global numeric variables, shared
with behaviours), `launcher.broadcast(name, data)`, `fetch(url, function (err, body) {…})`
(HTTPS only, 10 s timeout, 256 KB cap), `after(ms, fn)`, `log(…)` / `print(…)`.

**Sandbox**: no Java classes, no file system, no `eval` of host code; each hook call gets a CPU
budget (≈2 million instructions). A script that throws or runs too long is **disabled** and you
get a notice; the error (with line number) appears in the editor's console. Fix it and press
**Save & reload**. Scripts run only while their page is visible, like physics.

The editor has syntax highlighting, line numbers, **Check** (compiles without running) and the
console. Saved scripts take effect when you leave Edit Mode (the page's session restarts with
the new code — hot reload, no app restart).

#### Example 1 — tap counter that changes image at 10 taps

```js
function onTap(e) {
  this.vars.taps = (this.vars.taps || 0) + 1;
  this.say(String(this.vars.taps), 600);
  if (this.vars.taps == 10) {
    this.setImage("trophy");      // a sticker named "trophy" in your library
    this.emit(40, "#FFD54D");
    this.haptic("heavy");
  }
}
```

#### Example 2 — spinner that launches Spotify above 500 rpm

Use the *Fidget spinner* preset (or any object with a pinned dynamic body):

```js
var launched = false;
function onSpin(rpm) {
  if (rpm > 500 && !launched) {
    launched = true;
    this.haptic("double");
    launcher.openApp("com.spotify.music");
  }
  if (rpm < 100) launched = false;   // re-arm once it slows down
}
```

#### Example 3 — bounce when a WhatsApp notification arrives

Needs notification access (Settings → Permissions):

```js
function onNotification(app) {
  if (app == "com.whatsapp") {
    this.applyImpulse(0, -900);
    this.haptic("tick");
  }
}
```

All three are also one tap away in the editor's example chips.

### Desktop mode

- **Taskbar**: Settings → Panels → **Add desktop taskbar** (start button, taskbar, tray along the
  bottom), or add *Start*, *Taskbar* and *Tray* items to any panel. The taskbar shows pinned
  apps, recently used apps (grant **usage access** when the taskbar asks) and minimized widget
  windows. Long-press an app: *Open in window* / *Pin to taskbar*.
- **System tray**: clock, battery, Wi-Fi and the number of clearable notifications (needs
  notification access).
- **Start menu**: search (prefix, word starts and initials — "gm" finds Google Maps) and
  category chips from the Play category.
- **Widget windows**: long-press empty space → *Widget in a window*. Drag the title bar,
  resize from the corner, double-tap the title to maximize; minimize sends it to the taskbar.
- **Free-form apps**: *Open in window* uses `ActivityOptions.setLaunchBounds` when the device
  supports free-form windows (desktop mode, some foldables, or Developer options → *Enable
  freeform windows*); otherwise the app opens normally and the launcher says so.
- **Context menu**: long-press empty space → add widget / object / widget window, paste, page
  settings, wallpaper, edit home. Long-pressing an object still enters Edit Mode with it selected.
- **Clipboard**: the copy button in an object's floating toolbar copies it (with its group);
  paste on any page from the context menu (at your finger) or the Edit Mode toolbar.

### Performance

Physics, behaviours, scripts and idle animations pause when the screen is off, when the
launcher isn't visible (another app or an overlay is open), and in battery saver or below 15%
battery. **Settings → Performance**:

- *Keep animations & physics in battery saver* — the override.
- *Max dynamic objects per page* (default 30) — spawning stops at the cap.
- *Performance overlay* — FPS, active objects, physics bodies and physics step time on the
  home screen.

Physics sleeps when nothing moves, so a still page costs nothing per frame.

## Permissions & first run

The first-run screen deep-links to each step; all are optional:

1. **Default home app**: Home button handling and app shortcuts.
2. **Accessibility service** (`LauncherAccessibilityService`): lock screen, recents, notification shade,
   quick settings and volume-key long presses. It never reads window content.
3. **Notification access** (`MediaNotificationListener`): the media widget, "notification
   arrives" triggers / `onNotification`, and the tray's notification count. Only package names and
   the count are used; notification content is never read.
4. **Usage access** (`PACKAGE_USAGE_STATS`): only for the taskbar's recent apps.

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
physics/          JBox2D world, collider math, page physics host
objects/          Behaviour sessions, triggers, node runtimes, variables, presets
scripting/        Rhino sandbox (ScriptEngine), tokenizer, page script extension
desktop/          Taskbar recents, tray state, free-form launch, window / start-menu / clipboard logic
ui/               Compose UI: home, edit mode + inspector, objects, keyframe timeline, script editor, desktop, drawer, search, settings
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

`./gradlew testDebugUnitTest` runs JVM tests for the calculator, unit converter, letter recogniser, auto-switch rules,
profile JSON round-trips, sticker formats, smart guides, motion and keyframe math, collider math, behaviour logic,
the script sandbox (including the three README examples, CPU limit and no-Java checks) and the desktop window /
start-menu / clipboard logic.

## Licenses

Bundled fonts (Inter, Lora, JetBrains Mono, Comfortaa) are under the SIL Open Font License; see `licenses/fonts/`.

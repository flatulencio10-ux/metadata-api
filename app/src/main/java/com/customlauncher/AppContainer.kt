package com.customlauncher

import android.content.Context
import com.customlauncher.actions.ActionExecutor
import com.customlauncher.activities.ActivityAutoSwitcher
import com.customlauncher.actions.LauncherBus
import com.customlauncher.data.apps.AppRepository
import com.customlauncher.data.apps.IconLoader
import com.customlauncher.data.apps.IconPackManager
import com.customlauncher.data.apps.ShortcutRepository
import com.customlauncher.data.db.LauncherDatabase
import com.customlauncher.data.profile.ProfileArchive
import com.customlauncher.data.profile.ProfileRepository
import com.customlauncher.data.settings.LauncherPrefs
import com.customlauncher.data.settings.SecureStore
import com.customlauncher.data.stickers.StickerRepository
import com.customlauncher.runtime.AnimationSignals
import com.customlauncher.objects.RuntimeRegistry
import com.customlauncher.objects.SoundAndHaptics
import com.customlauncher.objects.TriggerSources
import com.customlauncher.objects.VariablePersistence
import com.customlauncher.runtime.PowerGovernor
import com.customlauncher.runtime.TiltSensor
import com.customlauncher.data.system.CalendarRepository
import com.customlauncher.data.system.ContactsRepository
import com.customlauncher.data.system.MediaRepository
import com.customlauncher.data.system.SystemStatsRepository
import com.customlauncher.widgets.football.FootballRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: one instance per process, created in [LauncherApp].
 * Everything is lazy so cold start only pays for what the first frame needs.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    /** Lives as long as the process; used for writes that must not be cancelled by UI. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bus = LauncherBus()
    val profileRepository by lazy { ProfileRepository(app, appScope) }
    val prefs by lazy { LauncherPrefs(app) }
    val secureStore by lazy { SecureStore(app) }
    val appRepository by lazy { AppRepository(app, appScope) }
    val iconPackManager by lazy { IconPackManager(app) }
    val iconLoader by lazy { IconLoader(app, iconPackManager) }
    val shortcutRepository by lazy { ShortcutRepository(app) }
    val database by lazy { LauncherDatabase.create(app) }
    val footballRepository by lazy { FootballRepository(database.cacheDao(), secureStore) }
    val systemStats by lazy { SystemStatsRepository(app) }
    val calendarRepository by lazy { CalendarRepository(app) }
    val contactsRepository by lazy { ContactsRepository(app) }
    val mediaRepository by lazy { MediaRepository(app) }
    val stickerRepository by lazy { StickerRepository(app, appScope) }
    val profileArchive by lazy { ProfileArchive(app, profileRepository) }
    val powerGovernor by lazy { PowerGovernor(app, profileRepository, appScope) }
    val tiltSensor by lazy { TiltSensor(app, appScope) }
    val animationSignals = AnimationSignals()
    val triggerSources by lazy { TriggerSources(app, appScope) }
    val soundAndHaptics by lazy { SoundAndHaptics(app) }
    val runtimes = RuntimeRegistry()
    val variables by lazy { VariablePersistence.load(app, appScope) }
    val autoSwitcher by lazy { ActivityAutoSwitcher(app, profileRepository, systemStats, appScope) }
    val actionExecutor by lazy {
        ActionExecutor(app, appScope, bus, appRepository, shortcutRepository, profileRepository)
    }
}

/** Shortcut to the container from any Context (activities, services, workers). */
val Context.container: AppContainer
    get() = (applicationContext as LauncherApp).container

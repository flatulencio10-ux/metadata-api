package com.customlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.customlauncher.core.model.PanelItem
import com.customlauncher.widgets.football.FootballRefreshWorker
import com.customlauncher.widgets.football.FootballWidget
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LauncherApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.autoSwitcher.start()
        // Entrance animations can play on unlock.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                container.animationSignals.emit(com.customlauncher.core.model.EntranceTrigger.UNLOCK)
            }
        }, android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT))

        // Keep the football refresh job scheduled if any football widget is placed.
        container.appScope.launch {
            val profile = container.profileRepository.profile.filterNotNull().first()
            val hasFootball = profile.activities.any { act ->
                act.pages.any { p -> p.widgets.any { it.type == FootballWidget.id } } ||
                    act.panels.any { panel -> panel.items.any { it is PanelItem.Widget && it.widget.type == FootballWidget.id } }
            }
            if (hasFootball) FootballRefreshWorker.schedule(this@LauncherApp)
        }
    }

    /** Global Coil loader with SVG support (football crests are often SVG). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}

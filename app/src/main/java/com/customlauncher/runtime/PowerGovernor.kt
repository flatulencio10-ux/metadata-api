package com.customlauncher.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.customlauncher.core.model.EntranceTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.customlauncher.data.profile.ProfileRepository

/**
 * Decides whether continuous work (idle animations, physics) may run: not in battery saver
 * and not below 15% battery, unless the user turned on "ignore battery saver".
 */
class PowerGovernor(context: Context, profiles: ProfileRepository, scope: CoroutineScope) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val saver = MutableStateFlow(power.isPowerSaveMode)
    private val _battery = MutableStateFlow(100)
    /** Battery level in percent (behaviour triggers). */
    val battery: StateFlow<Int> = _battery

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                saver.value = power.isPowerSaveMode
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0 && scale > 0) _battery.value = level * 100 / scale
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
    }

    /** True while idle loops and physics may run. */
    val continuousAllowed: StateFlow<Boolean> = combine(
        saver, _battery, profiles.profile.filterNotNull().map { it.performance.ignoreBatterySaver },
    ) { isSaver, level, override -> override || (!isSaver && level >= 15) }
        .stateIn(scope, SharingStarted.Eagerly, true)
}

/** Global moments that can start entrance animations. */
class AnimationSignals {
    private val _events = MutableSharedFlow<EntranceTrigger>(extraBufferCapacity = 8)
    val events: SharedFlow<EntranceTrigger> = _events.asSharedFlow()
    fun emit(trigger: EntranceTrigger) { _events.tryEmit(trigger) }
}

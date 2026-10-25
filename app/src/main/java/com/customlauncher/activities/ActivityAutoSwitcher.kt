package com.customlauncher.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.customlauncher.core.model.AutoSwitchRule
import com.customlauncher.data.profile.ProfileRepository
import com.customlauncher.data.system.SystemStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Switches the current activity automatically (time of day, WiFi network, charging state).
 * Only reacts when the *winning rule's target changes*, so a manual switch sticks until the
 * conditions change again.
 */
class ActivityAutoSwitcher(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val stats: SystemStatsRepository,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (job != null) return
        val rules = profiles.profile.filterNotNull().map { p -> p.autoSwitch.filter { it.enabled } }.distinctUntilChanged()
        // Only listen to charging / WiFi when a rule needs it.
        val charging = rules.map { r -> r.any { it is AutoSwitchRule.Charging } }.distinctUntilChanged()
            .flatMapLatest { needed -> if (needed) stats.chargingFlow() else flowOf(false) }
        val ssid = rules.map { r -> r.any { it is AutoSwitchRule.Wifi } }.distinctUntilChanged()
            .flatMapLatest { needed -> if (needed) wifiSsidFlow() else flowOf(null) }

        job = scope.launch {
            combine(rules, minuteTicker(), charging, ssid) { r, minute, isCharging, currentSsid ->
                AutoSwitchRules.evaluate(r, SwitchConditions(minute, isCharging, currentSsid))
            }
                .distinctUntilChanged()
                .collect { target ->
                    if (target != null) {
                        profiles.update { p ->
                            if (p.activities.any { it.id == target }) p.copy(currentActivityId = target) else p
                        }
                    }
                }
        }
    }

    private fun minuteTicker(): Flow<Int> = flow {
        while (true) {
            val now = LocalTime.now()
            emit(now.hour * 60 + now.minute)
            delay((60 - now.second) * 1000L)
        }
    }

    /** Emits the connected WiFi SSID (null when unknown / not on WiFi / no location permission). */
    @SuppressLint("MissingPermission")
    private fun wifiSsidFlow(): Flow<String?> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        fun ssidOf(caps: NetworkCapabilities?): String? {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                caps?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(WifiManager::class.java).connectionInfo
            }
            return info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID.trim('<', '>') && it != WifiManager.UNKNOWN_SSID }
        }
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(ssidOf(caps)) }
                override fun onLost(network: Network) { trySend(null) }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(ssidOf(caps)) }
                override fun onLost(network: Network) { trySend(null) }
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        trySend(null)
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

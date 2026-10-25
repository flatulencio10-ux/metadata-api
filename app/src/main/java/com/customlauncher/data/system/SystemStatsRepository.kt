package com.customlauncher.data.system

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class SystemStats(
    val batteryPercent: Int,
    val charging: Boolean,
    val batteryTempC: Float,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
)

/** Battery, storage and memory figures. Cheap enough to poll every few seconds. */
class SystemStatsRepository(private val context: Context) {

    fun snapshot(): SystemStats {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes

        val mem = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(mem)

        return SystemStats(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            batteryTempC = temp,
            storageUsedBytes = total - free,
            storageTotalBytes = total,
            ramUsedBytes = mem.totalMem - mem.availMem,
            ramTotalBytes = mem.totalMem,
        )
    }

    /** Emits whether the device is plugged in; used for activity auto-switching. */
    fun chargingFlow(): Flow<Boolean> = callbackFlow {
        fun current(intent: Intent?): Boolean {
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            return plugged != 0
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(current(intent))
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        trySend(current(sticky))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}

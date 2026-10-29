package com.customlauncher.runtime

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

/** Device tilt, each axis in -1..1 (x: left/right, y: towards/away), low-pass filtered. */
data class Tilt(val x: Float, val y: Float)

/**
 * One shared gravity-sensor listener for everything that reacts to tilt (parallax, physics
 * gravity). It is only registered while someone collects [tilt].
 */
class TiltSensor(context: Context, scope: CoroutineScope) {
    private val sensors = context.getSystemService(SensorManager::class.java)

    val tilt: SharedFlow<Tilt> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        var fx = 0f
        var fy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Low-pass filter: smooth out hand jitter.
                fx += 0.15f * ((-e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f) - fx)
                fy += 0.15f * ((e.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f) - fy)
                trySend(Tilt(fx, fy))
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(500), replay = 1)
}

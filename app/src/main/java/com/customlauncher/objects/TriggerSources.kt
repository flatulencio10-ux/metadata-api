package com.customlauncher.objects

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.customlauncher.core.model.HapticPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.io.File
import java.time.LocalTime
import kotlin.math.sqrt

/** Device-wide event sources for behaviour triggers. Sensors register only while used. */
class TriggerSources(private val context: Context, scope: CoroutineScope) {

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Package names of newly posted notifications (needs notification access). */
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()
    fun notificationPosted(packageName: String) { _notifications.tryEmit(packageName) }

    /** Emits once per shake (acceleration well above gravity), at most once a second. */
    val shakes: SharedFlow<Unit> = callbackFlow {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) { awaitClose { }; return@callbackFlow }
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val g = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                val now = SystemClock.uptimeMillis()
                if (g > 11f && now - last > 1000) { last = now; trySend(Unit) }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(500))

    /** Minute of the day, emitted at each minute boundary. */
    fun minutes(): Flow<Int> = flow {
        while (true) {
            val now = LocalTime.now()
            emit(now.hour * 60 + now.minute)
            delay((60 - now.second) * 1000L)
        }
    }
}

/** Short sounds and haptic patterns for behaviours and scripts. */
class SoundAndHaptics(private val context: Context) {
    private val tones by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    /** "tone:NAME" uses a ToneGenerator constant (e.g. PROP_ACK); "file:NAME" plays files/sounds/NAME. */
    fun play(source: String) {
        when {
            source.startsWith("tone:") -> {
                val name = source.removePrefix("tone:")
                val tone = runCatching { ToneGenerator::class.java.getField("TONE_$name").getInt(null) }
                    .getOrDefault(ToneGenerator.TONE_PROP_ACK)
                tones?.startTone(tone, 150)
            }
            source.startsWith("file:") -> {
                val f = File(File(context.filesDir, "sounds"), source.removePrefix("file:"))
                if (f.exists()) runCatching {
                    MediaPlayer().apply {
                        setDataSource(f.path)
                        setOnCompletionListener { it.release() }
                        prepare()
                        start()
                    }
                }
            }
        }
    }

    fun haptic(pattern: HapticPattern) {
        val v = vibrator ?: return
        val effect = when (pattern) {
            HapticPattern.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            HapticPattern.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            HapticPattern.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            HapticPattern.DOUBLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            HapticPattern.LONG -> VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { v.vibrate(effect) }
    }
}

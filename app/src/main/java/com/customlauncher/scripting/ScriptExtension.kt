package com.customlauncher.scripting

import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import com.customlauncher.AppContainer
import com.customlauncher.actions.UiCommand
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.HapticPattern
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.VarOp
import com.customlauncher.core.model.VarScope
import com.customlauncher.objects.BehaviorSession
import com.customlauncher.objects.ObjectEvent
import com.customlauncher.objects.SessionExtension
import com.customlauncher.physics.PhysicsWorld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** One console line of a script. */
data class ConsoleLine(val error: Boolean, val text: String, val atMs: Long = System.currentTimeMillis())

/** Per-object script output (logs and errors) shown in the editor's console. */
class ScriptConsole {
    private val lines = mutableStateMapOf<String, SnapshotStateList<ConsoleLine>>()
    /** Crash message of disabled scripts, by node id (cleared on reload). */
    val crashed = mutableStateMapOf<String, String>()

    fun of(nodeId: String): List<ConsoleLine> = lines[nodeId].orEmpty()

    fun append(nodeId: String, error: Boolean, text: String) {
        val list = lines.getOrPut(nodeId) { mutableStateListOf() }
        list += ConsoleLine(error, text.take(500))
        while (list.size > 100) list.removeAt(0)
    }

    fun clear(nodeId: String) { lines[nodeId]?.clear(); crashed.remove(nodeId) }
}

/**
 * Runs object scripts inside the page's behaviour session: routes gestures, collisions and
 * state changes to hooks, polls spin speed for onSpin, drives onTick at up to 30 Hz, and
 * forwards notifications. Everything stops with the session (page hidden, screen off).
 */
class ScriptExtension(private val c: AppContainer) : SessionExtension {
    private val http by lazy {
        OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    }

    override fun attach(session: BehaviorSession, nodes: List<Node>, physics: PhysicsWorld?): (ObjectEvent) -> Unit {
        val byId = nodes.associateBy { it.id }
        val scope = session.scope
        val scripts = LinkedHashMap<String, ScriptInstance>()

        val bridge = object : ScriptBridge {
            private fun rt(id: String) = c.runtimes[id]

            override fun get(nodeId: String, prop: String): Double {
                val n = byId[nodeId] ?: return 0.0
                val body = physics?.states?.get(nodeId)
                val rt = rt(nodeId)
                return when (prop) {
                    "x" -> (body?.x ?: n.x) + rt.dx
                    "y" -> (body?.y ?: n.y) + rt.dy
                    "rotation" -> body?.rotation ?: (n.rotation + rt.extraRotation)
                    "scale" -> n.scale * rt.scale
                    "rpm" -> abs(body?.rpm ?: 0f)
                    "vx" -> body?.vx ?: 0f
                    "vy" -> body?.vy ?: 0f
                    else -> 0f
                }.toDouble()
            }

            override fun set(nodeId: String, prop: String, value: Double) {
                val n = byId[nodeId] ?: return
                val rt = rt(nodeId)
                val v = value.toFloat().takeIf { it.isFinite() } ?: return
                val inPhysics = physics?.has(nodeId) == true
                when (prop) {
                    "x" -> if (inPhysics) physics!!.moveTo(nodeId, v, get(nodeId, "y").toFloat()) else { rt.motionJob?.cancel(); rt.dx = v - n.x }
                    "y" -> if (inPhysics) physics!!.moveTo(nodeId, get(nodeId, "x").toFloat(), v) else { rt.motionJob?.cancel(); rt.dy = v - n.y }
                    "rotation" -> rt.extraRotation = v - n.rotation
                    "scale" -> rt.scale = (v / n.scale.coerceAtLeast(0.01f)).coerceIn(0.05f, 20f)
                    "vx", "vy" -> if (inPhysics) {
                        val vx = if (prop == "vx") v else get(nodeId, "vx").toFloat()
                        val vy = if (prop == "vy") v else get(nodeId, "vy").toFloat()
                        physics!!.setVelocity(nodeId, vx, vy)
                    }
                }
            }

            override fun state(nodeId: String) = rt(nodeId).state
            override fun setState(nodeId: String, state: String) = session.setState(nodeId, state)

            override fun action(nodeId: String, name: String, args: List<Any?>) {
                fun num(i: Int, d: Float) = (args.getOrNull(i) as? Double)?.toFloat() ?: d
                fun str(i: Int) = args.getOrNull(i)?.toString()
                val a: BAction = when (name) {
                    "impulse" -> BAction.Impulse(num(0, 0f), num(1, -600f))
                    "spin" -> BAction.Torque(num(0, 300f))
                    "image" -> {
                        val wanted = str(0) ?: return
                        val lib = c.stickerRepository.library.value
                        val file = lib.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.file ?: lib.firstOrNull { it.file == wanted }?.file ?: wanted
                        BAction.SwapImage(file)
                    }
                    "play" -> BAction.PlayClip(str(0) ?: return)
                    "emit" -> BAction.EmitParticles(num(0, 30f).toInt().coerceIn(1, 200), parseColor(str(1)) ?: 0xFFFFD54F)
                    "haptic" -> BAction.Haptic(HapticPattern.entries.firstOrNull { it.name.equals(str(0), ignoreCase = true) } ?: HapticPattern.CLICK)
                    "say" -> BAction.Say(str(0) ?: "", num(1, 2500f).toInt())
                    "sound" -> BAction.PlaySound(str(0) ?: return)
                    "moveTo" -> BAction.MoveTo(num(0, 0f), num(1, 0f), num(2, 600f).toInt())
                    else -> return
                }
                scope.launch { session.run(nodeId, a) }
            }

            override fun getVar(global: Boolean, nodeId: String, name: String): Double? =
                c.variables.get(if (global) VarScope.GLOBAL else VarScope.OBJECT, nodeId, name, Double.NaN).takeUnless { it.isNaN() }

            override fun setVar(global: Boolean, nodeId: String, name: String, value: Double) {
                if (value.isNaN()) return
                c.variables.set(if (global) VarScope.GLOBAL else VarScope.OBJECT, nodeId, name, VarOp.SET, value)
            }

            override fun openApp(packageName: String): Boolean {
                val intent = c.app.packageManager.getLaunchIntentForPackage(packageName) ?: run {
                    c.bus.send(UiCommand.Message("App not installed: $packageName"))
                    return false
                }
                c.app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }

            override fun fetch(url: String, done: (String?, String?) -> Unit) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                                if (!r.isSuccessful) error("HTTP ${r.code}")
                                // Cap the body so a script can't pull huge downloads into memory.
                                r.body?.source()?.let { src -> src.request(MAX_BODY); src.buffer.readUtf8(minOf(src.buffer.size, MAX_BODY)) } ?: ""
                            }
                        }
                    }
                    result.fold({ done(null, it) }, { done(it.message ?: "Network error", null) })
                }
            }

            override fun after(ms: Long, block: () -> Unit) {
                scope.launch { delay(ms); block() }
            }

            override fun broadcast(fromId: String, name: String, data: Any?) {
                scope.launch { scripts.values.forEach { it.call("onMessage", name, data, fromId) } }
            }

            override fun log(nodeId: String, error: Boolean, message: String) = c.scriptConsole.append(nodeId, error, message)
        }

        nodes.filter { !it.script.isNullOrBlank() }.forEach { n ->
            c.scriptConsole.crashed.remove(n.id)
            scripts[n.id] = ScriptInstance(n.id, n.script!!, bridge) { msg ->
                c.scriptConsole.crashed[n.id] = msg
                c.bus.send(UiCommand.Message("Script of “${n.name.ifBlank { "object" }}” stopped: $msg"))
            }
        }
        if (scripts.isEmpty()) return { }

        fun each(hook: String, block: (ScriptInstance) -> Unit) = scripts.values.filter { it.has(hook) }.forEach(block)

        if (scripts.values.any { it.has("onTick") }) scope.launch {
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1e9
                if (dt >= 1 / 30.0) {
                    last = now
                    each("onTick") { it.call("onTick", dt.coerceAtMost(0.25)) }
                }
            }
        }
        if (scripts.values.any { it.has("onSpin") }) scope.launch {
            val previous = HashMap<String, Double>()
            while (isActive) {
                each("onSpin") { s ->
                    val rpm = bridge.get(s.nodeId, "rpm")
                    val before = previous.put(s.nodeId, rpm) ?: 0.0
                    // Report while spinning, plus once when it comes to rest.
                    if (rpm > 0.5 || before > 0.5) s.call("onSpin", rpm)
                }
                delay(66)
            }
        }
        if (scripts.values.any { it.has("onNotification") }) scope.launch {
            c.triggerSources.notifications.collect { pkg -> each("onNotification") { it.call("onNotification", pkg) } }
        }

        return { e ->
            val s = scripts[e.nodeId]
            when (e) {
                is ObjectEvent.Tap -> s?.call("onTap", mapOf("type" to "tap"))
                is ObjectEvent.DoubleTap -> s?.call("onDoubleTap", mapOf("type" to "doubletap"))
                is ObjectEvent.LongPress -> s?.call("onLongPress", mapOf("type" to "longpress"))
                is ObjectEvent.DragStart -> s?.call("onDragStart", mapOf("type" to "dragstart"))
                is ObjectEvent.DragEnd -> s?.call("onDragEnd", mapOf("type" to "dragend", "speed" to e.speed))
                is ObjectEvent.Collided -> s?.call("onCollide", e.other, e.impulse)
                is BehaviorSession.StateChanged -> s?.call("onState", e.state)
            }
        }
    }

    private fun parseColor(s: String?): Long? {
        val hex = s?.removePrefix("#") ?: return null
        val v = hex.toLongOrNull(16) ?: return null
        return when (hex.length) { 6 -> 0xFF000000 or v; 8 -> v; else -> null }
    }

    private companion object {
        const val MAX_BODY = 256L * 1024
    }
}

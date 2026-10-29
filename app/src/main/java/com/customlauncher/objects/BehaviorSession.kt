package com.customlauncher.objects

import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import com.customlauncher.AppContainer
import com.customlauncher.core.anim.MotionMath
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.BTrigger
import com.customlauncher.core.model.Behavior
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.EntranceTrigger
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeState
import com.customlauncher.core.model.TiltDirection
import com.customlauncher.core.model.VarScope
import com.customlauncher.physics.PhysicsWorld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Runs the behaviours of the visible page's objects: listens to every trigger source that
 * some behaviour uses, checks conditions, and executes action lists (in parallel per
 * behaviour, sequentially within one). Lives exactly as long as the page is active.
 */
class BehaviorSession(
    private val c: AppContainer,
    /** Lives as long as the session; scripts launch their work here. */
    val scope: CoroutineScope,
    nodes: List<Node>,
    private val physics: PhysicsWorld?,
    /** Lets scripts and other hooks observe events. */
    private val listeners: List<(ObjectEvent) -> Unit> = emptyList(),
    /** Adds a preset object next to [nearNodeId] (physics sandbox). */
    private val onSpawn: (preset: String, nearNodeId: String) -> Unit = { _, _ -> },
) {
    private val nodes = nodes.associateBy { it.id }
    private val vars = c.variables
    private val runtimes = c.runtimes

    private fun logicNodes() = nodes.values.filter { it.logic != null }

    private fun stateOf(n: Node, name: String): NodeState? = n.logic?.states?.firstOrNull { it.name == name }

    /** Object behaviours plus those of the current state. */
    private fun activeBehaviors(n: Node): List<Behavior> {
        val logic = n.logic ?: return emptyList()
        return logic.behaviors + (stateOf(n, runtimes[n.id].state)?.behaviors.orEmpty())
    }

    private fun allTriggers(): List<BTrigger> = logicNodes().flatMap { n ->
        n.logic!!.behaviors.map { it.trigger } + n.logic.states.flatMap { s -> s.behaviors.map { it.trigger } }
    }

    fun start() {
        vars.seed(VarScope.GLOBAL, "", c.profileRepository.profile.value?.globalVariables.orEmpty())
        logicNodes().forEach { n ->
            val logic = n.logic!!
            vars.seed(VarScope.OBJECT, n.id, logic.variables)
            if (runtimes[n.id].state == "idle" && logic.initialState != "idle") setState(n.id, logic.initialState)
        }
        fire { it is BTrigger.PageShown }
        val triggers = allTriggers()
        if (triggers.any { it is BTrigger.Unlock }) scope.launch {
            c.animationSignals.events.collect { if (it == EntranceTrigger.UNLOCK) fire { t -> t is BTrigger.Unlock } }
        }
        if (triggers.any { it is BTrigger.TimeOfDay }) scope.launch {
            c.triggerSources.minutes().collect { m -> fire { it is BTrigger.TimeOfDay && it.minute == m } }
        }
        if (triggers.any { it is BTrigger.BatteryBelow }) scope.launch {
            var prev = c.powerGovernor.battery.value
            c.powerGovernor.battery.collect { now ->
                fire { it is BTrigger.BatteryBelow && prev >= it.percent && now < it.percent }
                prev = now
            }
        }
        if (triggers.any { it is BTrigger.NotificationFrom }) scope.launch {
            c.triggerSources.notifications.collect { pkg ->
                fire { it is BTrigger.NotificationFrom && (it.packageName.isBlank() || it.packageName == pkg) }
            }
        }
        if (triggers.any { it is BTrigger.MusicStarted || it is BTrigger.MusicPaused }) scope.launch {
            c.mediaRepository.ensureAttached()
            c.mediaRepository.state.map { it?.isPlaying == true }.distinctUntilChanged().drop(1).collect { playing ->
                fire { if (playing) it is BTrigger.MusicStarted else it is BTrigger.MusicPaused }
            }
        }
        if (triggers.any { it is BTrigger.Shaken }) scope.launch {
            c.triggerSources.shakes.collect { fire { it is BTrigger.Shaken } }
        }
        if (triggers.any { it is BTrigger.Tilted }) scope.launch {
            val tilted = HashMap<TiltDirection, Boolean>()
            c.tiltSensor.tilt.collect { t ->
                TiltDirection.entries.forEach { d ->
                    val v = when (d) { TiltDirection.LEFT -> -t.x; TiltDirection.RIGHT -> t.x; TiltDirection.FORWARD -> -t.y; TiltDirection.BACK -> t.y }
                    val was = tilted[d] ?: false
                    // Hysteresis so jitter around the threshold doesn't re-fire.
                    if (!was && v > 0.45f) { tilted[d] = true; fire { it is BTrigger.Tilted && it.direction == d } }
                    if (was && v < 0.3f) tilted[d] = false
                }
            }
        }
        logicNodes().forEach { n ->
            (n.logic!!.behaviors + n.logic.states.flatMap { it.behaviors }).forEach { b ->
                when (val t = b.trigger) {
                    is BTrigger.Every -> scope.launch {
                        while (isActive) { delay((t.seconds * 1000).toLong().coerceAtLeast(100)); fireOne(n, b) }
                    }
                    is BTrigger.RandomChance -> scope.launch {
                        while (isActive) { delay(1000); if (Random.nextFloat() * 100f < t.percentPerSecond) fireOne(n, b) }
                    }
                    else -> Unit
                }
            }
        }
        if (physics != null || triggers.any { it is BTrigger.ZoneEnter || it is BTrigger.ZoneLeave }) scope.launch { pollMotion() }
    }

    /** ~15 Hz: spin thresholds, automatic "spinning" state and zone enter/leave. */
    private suspend fun pollMotion() {
        while (scope.isActive) {
            logicNodes().forEach { n ->
                val rt = runtimes[n.id]
                val body = physics?.states?.get(n.id)
                val rpm = abs(body?.rpm ?: 0f)
                activeBehaviors(n).forEach { b ->
                    val t = b.trigger
                    if (t is BTrigger.SpinCrosses && BehaviorLogic.crossed(rt.lastRpm, rpm, t.rpm, t.upward)) fireOne(n, b)
                }
                if (stateOf(n, "spinning") != null) {
                    if (rt.state != "spinning" && rpm > 60f) setState(n.id, "spinning")
                    if (rt.state == "spinning" && rpm < 20f) setState(n.id, n.logic!!.initialState)
                }
                rt.lastRpm = rpm
                val cx = (body?.x ?: n.x) + rt.dx + n.width / 2f
                val cy = (body?.y ?: n.y) + rt.dy + n.height / 2f
                activeBehaviors(n).forEach { b ->
                    val zone = when (val t = b.trigger) { is BTrigger.ZoneEnter -> t.zone; is BTrigger.ZoneLeave -> t.zone; else -> null }
                    if (zone != null) {
                        val inside = zone.contains(cx, cy)
                        val was = rt.inZone[b.id]
                        rt.inZone[b.id] = inside
                        if (was != null && was != inside) {
                            if (inside && b.trigger is BTrigger.ZoneEnter) fireOne(n, b)
                            if (!inside && b.trigger is BTrigger.ZoneLeave) fireOne(n, b)
                        }
                    }
                }
            }
            delay(66)
        }
    }

    /** Object gestures and collisions. */
    fun onEvent(e: ObjectEvent) {
        listeners.forEach { it(e) }
        val n = nodes[e.nodeId] ?: return
        val rt = runtimes[n.id]
        when (e) {
            is ObjectEvent.Tap -> {
                rt.tapKey++
                if (stateOf(n, "tapped") != null) scope.launch {
                    setState(n.id, "tapped")
                    delay((stateOf(n, "tapped")!!.transitionMs + 400).toLong())
                    if (rt.state == "tapped") setState(n.id, n.logic!!.initialState)
                }
                fireNode(n) { it is BTrigger.Tap }
            }
            is ObjectEvent.DoubleTap -> fireNode(n) { it is BTrigger.DoubleTap }
            is ObjectEvent.LongPress -> fireNode(n) { it is BTrigger.LongPress }
            is ObjectEvent.DragStart -> {
                if (stateOf(n, "dragging") != null) setState(n.id, "dragging")
                fireNode(n) { it is BTrigger.DragStart }
            }
            is ObjectEvent.DragEnd -> {
                if (rt.state == "dragging") setState(n.id, n.logic?.initialState ?: "idle")
                fireNode(n) { it is BTrigger.DragEnd || (it is BTrigger.Thrown && e.speed >= it.minSpeed) }
            }
            is ObjectEvent.Collided -> fireNode(n) {
                it is BTrigger.Collides && e.impulse >= it.minImpulse && (it.withNodeId == null || it.withNodeId == e.other)
            }
            else -> Unit
        }
    }

    private fun fire(match: (BTrigger) -> Boolean) = logicNodes().forEach { fireNode(it, match) }

    private fun fireNode(n: Node, match: (BTrigger) -> Boolean) {
        activeBehaviors(n).filter { it.enabled && match(it.trigger) }.forEach { fireOne(n, it) }
    }

    private fun fireOne(n: Node, b: Behavior) {
        if (!b.enabled) return
        if (!BehaviorLogic.conditionsHold(b.conditions, n.id, runtimes[n.id].state, vars)) return
        scope.launch { runActions(n.id, b.actions) }
    }

    suspend fun runActions(nodeId: String, actions: List<BAction>) {
        for (a in actions) run(nodeId, a)
    }

    /** Executes one action for [nodeId]. Public so scripts can reuse it. */
    suspend fun run(nodeId: String, a: BAction) {
        val n = nodes[nodeId] ?: return
        val rt = runtimes[nodeId]
        when (a) {
            is BAction.PlayClip -> rt.clip = a.clip to ((rt.clip?.second ?: 0) + 1)
            is BAction.PlayEffect -> rt.effect = a.effect to ((rt.effect?.second ?: 0) + 1)
            is BAction.SwapImage -> rt.image = a.file
            is BAction.SetState -> setState(nodeId, a.state)
            is BAction.PlaySprite -> {
                rt.sprite = SpriteRun(a.file, a.cols, a.rows, a.fps, a.loops, SystemClock.uptimeMillis())
                val frames = (a.cols * a.rows).coerceAtLeast(1)
                delay((frames * a.loops.coerceAtLeast(1) * 1000f / a.fps.coerceAtLeast(1f)).toLong())
                rt.sprite = null
            }
            is BAction.Impulse -> physics?.applyImpulse(nodeId, a.dx, a.dy) ?: animateOffset(rt, rt.dx + a.dx * 0.2f, rt.dy + a.dy * 0.2f, 400, Easing.OVERSHOOT)
            is BAction.Torque -> if (physics?.has(nodeId) == true) physics.applyTorque(nodeId, a.rpm) else rt.effect = com.customlauncher.core.model.TapEffect.SPIN to ((rt.effect?.second ?: 0) + 1)
            is BAction.MoveTo -> if (physics?.has(nodeId) == true) physics.moveTo(nodeId, a.x, a.y)
            else animateOffset(rt, a.x - n.x, a.y - n.y, a.durationMs, a.easing)
            is BAction.OffsetTo -> if (a.durationMs <= 0) { rt.motionJob?.cancel(); rt.dx = a.dx; rt.dy = a.dy }
            else animateOffset(rt, a.dx, a.dy, a.durationMs, a.easing)
            is BAction.Spawn -> onSpawn(a.preset, nodeId)
            is BAction.FollowFinger -> rt.followFinger = a.enabled
            is BAction.FollowPath -> followPath(rt, a)
            is BAction.Orbit -> orbit(n, rt, a)
            is BAction.EmitParticles -> {
                val burst = Burst(a.count, a.color, a.spread, SystemClock.uptimeMillis(), Random.nextInt())
                rt.bursts += burst
                scope.launch { delay(1400); rt.bursts.remove(burst) }
            }
            is BAction.PlaySound -> c.soundAndHaptics.play(a.source)
            is BAction.Haptic -> c.soundAndHaptics.haptic(a.pattern)
            is BAction.Say -> {
                rt.bubble = a.text
                delay(a.durationMs.toLong())
                if (rt.bubble == a.text) rt.bubble = null
            }
            is BAction.ChangeOther -> run(a.targetId, a.action)
            is BAction.SetVariable -> vars.set(a.scope, nodeId, a.name, a.op, a.value)
            is BAction.Launcher -> c.actionExecutor.run(a.action)
            is BAction.TogglePhysics -> { rt.physicsEnabled = a.enabled; physics?.setEnabled(nodeId, a.enabled) }
            is BAction.Wait -> delay(a.ms.toLong())
            is BAction.Randomize -> a.actions.randomOrNull()?.let { run(nodeId, it) }
        }
    }

    fun setState(nodeId: String, name: String) {
        val n = nodes[nodeId] ?: return
        val rt = runtimes[nodeId]
        val state = stateOf(n, name)
        rt.state = name
        rt.transitionMs = state?.transitionMs ?: 200
        rt.image = state?.image
        rt.stateAnimation = state?.animation
        state?.sound?.let { c.soundAndHaptics.play(it) }
        listeners.forEach { it(StateChanged(nodeId, name)) }
    }

    /** Synthetic event so scripts' onState(name) hook can run. */
    data class StateChanged(override val nodeId: String, val state: String) : ObjectEvent

    private suspend fun animate(durationMs: Int, easing: Easing, step: (Float) -> Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { (it - start) / 1e6f / durationMs.coerceAtLeast(1) }
            step(MotionMath.ease(easing, t.coerceAtMost(1f)))
            if (t >= 1f) break
        }
    }

    private suspend fun animateOffset(rt: NodeRuntime, toX: Float, toY: Float, durationMs: Int, easing: Easing) {
        rt.motionJob?.cancel()
        val fromX = rt.dx
        val fromY = rt.dy
        animate(durationMs, easing) { p -> rt.dx = fromX + (toX - fromX) * p; rt.dy = fromY + (toY - fromY) * p }
    }

    private suspend fun followPath(rt: NodeRuntime, a: BAction.FollowPath) {
        if (a.path.size < 2) return
        val baseX = rt.dx
        val baseY = rt.dy
        repeat(a.loops.coerceAtLeast(1)) {
            animate(a.durationMs, Easing.LINEAR) { p ->
                val pt = MotionMath.pointAlong(a.path, p)
                rt.dx = baseX + pt.x; rt.dy = baseY + pt.y
            }
        }
        rt.dx = baseX; rt.dy = baseY
    }

    private suspend fun orbit(n: Node, rt: NodeRuntime, a: BAction.Orbit) {
        val target = nodes[a.targetId] ?: return
        val trt = runtimes[target.id]
        val start = withFrameNanos { it }
        while (true) {
            val elapsed = withFrameNanos { (it - start) / 1e9f }
            if (elapsed * 1000 >= a.durationMs) break
            val tb = physics?.states?.get(target.id)
            val tcx = (tb?.x ?: target.x) + trt.dx + target.width / 2f
            val tcy = (tb?.y ?: target.y) + trt.dy + target.height / 2f
            val angle = 2 * PI * a.rpm / 60.0 * elapsed
            rt.dx = tcx + (cos(angle) * a.radius).toFloat() - n.width / 2f - n.x
            rt.dy = tcy + (sin(angle) * a.radius).toFloat() - n.height / 2f - n.y
        }
    }

}

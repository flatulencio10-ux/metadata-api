package com.customlauncher.objects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.customlauncher.container
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.BTrigger
import com.customlauncher.core.model.Node
import com.customlauncher.physics.PhysicsWorld
import com.customlauncher.ui.LauncherViewModel
import kotlinx.coroutines.awaitCancellation

/** Extra event observers (scripts) attached to the page's behaviour session. */
fun interface SessionExtension {
    fun attach(session: BehaviorSession, nodes: List<Node>, physics: PhysicsWorld?): (ObjectEvent) -> Unit
}

/** Runs a [BehaviorSession] for the page's objects while the page is visible. */
@Composable
fun BehaviorHost(
    nodes: List<Node>,
    physics: PhysicsWorld?,
    active: Boolean,
    vm: LauncherViewModel,
    extensions: List<SessionExtension> = emptyList(),
) {
    val c = LocalContext.current.container
    // Restart only when behaviours change, not when positions do (running actions survive).
    val key = nodes.map { Triple(it.id, it.logic, it.script) }
    LaunchedEffect(key, physics, active, vm.scriptGeneration) {
        if (!active || nodes.none { it.logic != null || !it.script.isNullOrBlank() }) return@LaunchedEffect
        val hooks = ArrayList<(ObjectEvent) -> Unit>()
        val session = BehaviorSession(
            c, this, nodes, physics,
            listeners = listOf<(ObjectEvent) -> Unit> { e -> hooks.forEach { it(e) } },
            onSpawn = vm::spawnPreset,
        )
        extensions.forEach { hooks += it.attach(session, nodes, physics) }
        val listener: (ObjectEvent) -> Unit = session::onEvent
        vm.objectEventListener = listener
        session.start()
        try {
            awaitCancellation()
        } finally {
            if (vm.objectEventListener === listener) vm.objectEventListener = null
        }
    }
}

/** Nodes that take touches in the object layer (gesture triggers, follow-finger, physics). */
fun touchableObjects(nodes: List<Node>, scripted: (Node) -> Boolean = { false }): List<Node> = nodes.filter { n ->
    !n.hidden && (scripted(n) || n.logic?.let { l ->
        val behaviors = l.behaviors + l.states.flatMap { it.behaviors }
        behaviors.any { b ->
            b.trigger is BTrigger.Tap || b.trigger is BTrigger.DoubleTap || b.trigger is BTrigger.LongPress ||
                b.trigger is BTrigger.DragStart || b.trigger is BTrigger.DragEnd || b.trigger is BTrigger.Thrown ||
                b.actions.any { it is BAction.FollowFinger }
        } || l.states.any { it.name == "tapped" || it.name == "dragging" }
    } == true)
}

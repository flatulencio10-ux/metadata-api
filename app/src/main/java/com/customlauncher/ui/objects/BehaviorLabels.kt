package com.customlauncher.ui.objects

import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.BCondition
import com.customlauncher.core.model.BTrigger
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.VarScope

/** Human-readable labels for the rule editor ("When tapped · if taps ≥ 10 · do Say…"). */
object BehaviorLabels {
    fun trigger(t: BTrigger): String = when (t) {
        BTrigger.Tap -> "tapped"
        BTrigger.DoubleTap -> "double-tapped"
        BTrigger.LongPress -> "long-pressed"
        BTrigger.DragStart -> "drag starts"
        BTrigger.DragEnd -> "drag ends"
        is BTrigger.Thrown -> "thrown (> ${t.minSpeed.toInt()} dp/s)"
        is BTrigger.SpinCrosses -> "spin goes ${if (t.upward) "above" else "below"} ${t.rpm.toInt()} rpm"
        is BTrigger.Collides -> if (t.withNodeId == null) "hits anything" else "hits another object"
        is BTrigger.ZoneEnter -> "enters a zone"
        is BTrigger.ZoneLeave -> "leaves a zone"
        BTrigger.PageShown -> "page is shown"
        BTrigger.Unlock -> "screen unlocks"
        is BTrigger.TimeOfDay -> "it is %02d:%02d".format(t.minute / 60, t.minute % 60)
        is BTrigger.BatteryBelow -> "battery drops below ${t.percent}%"
        is BTrigger.NotificationFrom -> if (t.packageName.isBlank()) "any notification arrives" else "notification from ${t.packageName}"
        BTrigger.MusicStarted -> "music starts"
        BTrigger.MusicPaused -> "music pauses"
        BTrigger.Shaken -> "phone is shaken"
        is BTrigger.Tilted -> "phone tilts ${t.direction.name.lowercase()}"
        is BTrigger.Every -> "every ${t.seconds} s"
        is BTrigger.RandomChance -> "randomly (${t.percentPerSecond}%/s)"
    }

    fun condition(c: BCondition): String = when (c) {
        is BCondition.Variable -> "${if (c.scope == VarScope.GLOBAL) "global." else ""}${c.name} ${c.op.symbol} ${fmt(c.value)}"
        is BCondition.StateIs -> "state is ${c.state}"
        is BCondition.Chance -> "${c.percent.toInt()}% chance"
    }

    fun action(a: BAction, profile: LayoutProfile?): String = when (a) {
        is BAction.PlayClip -> "play clip \"${a.clip}\""
        is BAction.PlayEffect -> a.effect.label.lowercase()
        is BAction.SwapImage -> "swap image"
        is BAction.SetState -> "go to state ${a.state}"
        is BAction.PlaySprite -> "play sprite ${a.cols}×${a.rows}"
        is BAction.Impulse -> "push (${a.dx.toInt()}, ${a.dy.toInt()})"
        is BAction.Torque -> "spin ${a.rpm.toInt()} rpm"
        is BAction.MoveTo -> "move to (${a.x.toInt()}, ${a.y.toInt()})"
        is BAction.OffsetTo -> "fly to offset (${a.dx.toInt()}, ${a.dy.toInt()})"
        is BAction.Spawn -> "spawn a ${a.preset}"
        is BAction.FollowFinger -> if (a.enabled) "follow finger" else "stop following finger"
        is BAction.FollowPath -> "follow path"
        is BAction.Orbit -> "orbit another object"
        is BAction.EmitParticles -> "burst ${a.count} particles"
        is BAction.PlaySound -> "sound ${a.source.substringAfter(':')}"
        is BAction.Haptic -> "haptic ${a.pattern.name.lowercase()}"
        is BAction.Say -> "say \"${a.text}\""
        is BAction.ChangeOther -> "other object: ${action(a.action, profile)}"
        is BAction.SetVariable -> "${if (a.scope == VarScope.GLOBAL) "global." else ""}${a.name} ${if (a.op.name == "ADD") "+=" else "="} ${fmt(a.value)}"
        is BAction.Launcher -> ActionCatalog.label(a.action, profile)
        is BAction.TogglePhysics -> if (a.enabled) "physics on" else "physics off"
        is BAction.Wait -> "wait ${a.ms} ms"
        is BAction.Randomize -> "one of ${a.actions.size} at random"
    }

    private fun fmt(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()
}

package com.customlauncher.objects

import com.customlauncher.core.model.Action
import com.customlauncher.core.model.AnimTiming
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.BCondition
import com.customlauncher.core.model.BTrigger
import com.customlauncher.core.model.Behavior
import com.customlauncher.core.model.BodyType
import com.customlauncher.core.model.ColliderShape
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.HapticPattern
import com.customlauncher.core.model.IdleEffect
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.NodeState
import com.customlauncher.core.model.ObjectLogic
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.PhysicsSpec
import com.customlauncher.core.model.SpeedVisuals
import com.customlauncher.core.model.TapEffect
import com.customlauncher.core.model.TiltDirection
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.core.model.WorldConfig
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widgets.objects.ObjectArtWidget

/**
 * A ready-made interactive object. [art] = procedural drawing (null = template only); the
 * physics / behaviours / animation / speed visuals can also be applied to ANY node
 * (e.g. turn your own nimbus image into a "cloud rider").
 */
data class ObjectPreset(
    val id: String,
    val name: String,
    val description: String,
    val art: String?,
    val width: Float,
    val height: Float,
    val color: Long = 0xFF7C5CFF,
    val physics: PhysicsSpec? = null,
    val logic: ObjectLogic? = null,
    val animation: NodeAnimations? = null,
    val speedVisuals: SpeedVisuals? = null,
)

object Presets {
    private fun toggleLogic(): ObjectLogic = ObjectLogic(
        initialState = "off",
        states = listOf(NodeState("off"), NodeState("on")),
        behaviors = listOf(
            Behavior(
                name = "Turn on", trigger = BTrigger.Tap, conditions = listOf(BCondition.StateIs("off")),
                actions = listOf(BAction.SetState("on"), BAction.Haptic(HapticPattern.HEAVY), BAction.Launcher(Action.ToggleFlashlight)),
            ),
            Behavior(
                name = "Turn off", trigger = BTrigger.Tap, conditions = listOf(BCondition.StateIs("on")),
                actions = listOf(BAction.SetState("off"), BAction.Haptic(HapticPattern.CLICK), BAction.Launcher(Action.ToggleFlashlight)),
            ),
        ),
    )

    val spinner = ObjectPreset(
        "spinner", "Fidget spinner", "Flick to spin. Slow decay, motion blur, zoetrope at high speed, tick per turn.",
        art = "spinner", width = 180f, height = 180f, color = 0xFF3DAEE9,
        physics = PhysicsSpec(type = BodyType.DYNAMIC, collider = ColliderShape.CIRCLE, pinned = true, angularDamping = 0.08f,
            linearDamping = 0f, gravityScale = 0f, mass = 1f),
        speedVisuals = SpeedVisuals(motionBlur = true, blurFromRpm = 120f, framesFromRpm = 700f, tickPerTurn = true),
        logic = ObjectLogic(behaviors = listOf(
            Behavior(name = "Whoa", trigger = BTrigger.SpinCrosses(1500f), actions = listOf(BAction.Say("WHOA! 🌀", 1500), BAction.Haptic(HapticPattern.HEAVY))),
        )),
    )

    val ball = ObjectPreset(
        "ball", "Bouncy ball", "Throw it around: it bounces off edges and widgets.",
        art = "ball", width = 72f, height = 72f, color = 0xFFF67400,
        physics = PhysicsSpec(type = BodyType.DYNAMIC, collider = ColliderShape.CIRCLE, restitution = 0.85f, friction = 0.3f, mass = 0.6f),
        speedVisuals = SpeedVisuals(motionBlur = false, stretchWhenFast = true, trails = true, sparksOnCollision = true),
    )

    /** "Fly-around": float idle; tap = arc across the screen, loop off one edge, return with overshoot. */
    val cloudRider = ObjectPreset(
        "cloud", "Cloud rider", "Floats; tap and it zooms off one edge, comes back from the other and settles. Apply it to your own image!",
        art = "cloud", width = 160f, height = 100f, color = 0xFFFFFFFF,
        animation = NodeAnimations(idle = IdleEffect.FLOAT, idleTiming = AnimTiming(durationMs = 3200, easing = Easing.LINEAR, repeat = -1)),
        logic = ObjectLogic(behaviors = listOf(
            Behavior(name = "Fly around", trigger = BTrigger.Tap, actions = listOf(
                BAction.EmitParticles(24, 0xFFFFFFFF, 0.6f),
                BAction.OffsetTo(260f, -150f, 420, Easing.EASE),
                BAction.EmitParticles(14, 0xFFBFE8FF, 0.4f),
                BAction.OffsetTo(760f, -60f, 320, Easing.LINEAR),
                BAction.OffsetTo(-760f, -60f, 0),
                BAction.OffsetTo(-220f, -30f, 360, Easing.LINEAR),
                BAction.OffsetTo(0f, 0f, 750, Easing.OVERSHOOT),
                BAction.EmitParticles(16, 0xFFFFFFFF, 0.35f),
            )),
        )),
    )

    val pendulum = ObjectPreset(
        "pendulum", "Pendulum", "Hangs from a pin; swings when tapped or when the phone tilts.",
        art = "pendulum", width = 64f, height = 64f, color = 0xFFFDBC4B,
        physics = PhysicsSpec(type = BodyType.DYNAMIC, collider = ColliderShape.CIRCLE, pinned = true, pinX = 0.5f, pinY = -2f,
            angularDamping = 0.05f, linearDamping = 0.05f, mass = 2f),
        logic = ObjectLogic(behaviors = listOf(
            Behavior(name = "Push", trigger = BTrigger.Tap, actions = listOf(BAction.Impulse(450f, 0f))),
            Behavior(name = "Tilt left", trigger = BTrigger.Tilted(TiltDirection.LEFT), actions = listOf(BAction.Impulse(-320f, 0f))),
            Behavior(name = "Tilt right", trigger = BTrigger.Tilted(TiltDirection.RIGHT), actions = listOf(BAction.Impulse(320f, 0f))),
        )),
    )

    val blob = ObjectPreset(
        "blob", "Stress blob", "Squash & stretch when pressed, jiggles when released. Drag it anywhere.",
        art = "blob", width = 120f, height = 110f, color = 0xFFFF6FB5,
        logic = ObjectLogic(behaviors = listOf(
            Behavior(name = "Grab", trigger = BTrigger.PageShown, actions = listOf(BAction.FollowFinger(true))),
            Behavior(name = "Squish", trigger = BTrigger.DragStart, actions = listOf(BAction.PlayEffect(TapEffect.SQUASH), BAction.Haptic(HapticPattern.TICK))),
            Behavior(name = "Jiggle", trigger = BTrigger.DragEnd, actions = listOf(BAction.PlayEffect(TapEffect.SQUASH))),
            Behavior(name = "Poke", trigger = BTrigger.Tap, actions = listOf(BAction.PlayEffect(TapEffect.SQUASH), BAction.Haptic(HapticPattern.TICK))),
        )),
    )

    val lever = ObjectPreset(
        "lever", "Lever (flashlight)", "Physical toggle: pull it to turn the flashlight on/off. Change the action in Behaviors.",
        art = "lever", width = 90f, height = 130f, color = 0xFF27AE60, logic = toggleLogic(),
    )

    val toggleSwitch = ObjectPreset(
        "switch", "Switch", "A big tactile on/off switch that runs any action.",
        art = "switch", width = 130f, height = 70f, color = 0xFF1D99F3, logic = toggleLogic(),
    )

    val knob = ObjectPreset(
        "knob", "Knob", "Turn it with a finger; heavy damping and detent ticks.",
        art = "knob", width = 110f, height = 110f, color = 0xFF8E44AD,
        physics = PhysicsSpec(type = BodyType.DYNAMIC, collider = ColliderShape.CIRCLE, pinned = true, angularDamping = 4f, gravityScale = 0f),
        logic = ObjectLogic(behaviors = listOf(Behavior(name = "Detent", trigger = BTrigger.SpinCrosses(8f), actions = listOf(BAction.Haptic(HapticPattern.TICK))))),
    )

    val spawner = ObjectPreset(
        "spawner", "Spawn button", "Tap to drop a new ball (physics sandbox).",
        art = "spawner", width = 72f, height = 72f, color = 0xFF27AE60,
        logic = ObjectLogic(behaviors = listOf(Behavior(name = "Spawn", trigger = BTrigger.Tap, actions = listOf(BAction.Spawn("ball"), BAction.Haptic(HapticPattern.CLICK))))),
    )

    val all = listOf(spinner, ball, cloudRider, pendulum, blob, lever, toggleSwitch, knob, spawner)

    fun find(id: String) = all.firstOrNull { it.id == id }

    /** A new art node for [preset] centred at (cx, cy). */
    fun node(preset: ObjectPreset, cx: Float, cy: Float, z: Int = 0): Node = WidgetInstance(
        type = ObjectArtWidget.id,
        x = cx - preset.width / 2f, y = cy - preset.height / 2f,
        width = preset.width, height = preset.height, z = z,
        name = preset.name,
        style = WidgetStyle(useCardStyle = false, backgroundColor = 0x00000000, paddingDp = 0f),
        config = WidgetConfig().with(ObjectArtWidget.ART, preset.art ?: "ball").with(ObjectArtWidget.COLOR, preset.color).json,
        physics = preset.physics, logic = preset.logic, animation = preset.animation, speedVisuals = preset.speedVisuals,
    )

    /** Copies a template's physics, behaviours, animation and speed visuals onto any node. */
    fun applyTo(node: Node, preset: ObjectPreset): Node = node.copy(
        physics = preset.physics ?: node.physics,
        logic = preset.logic ?: node.logic,
        animation = preset.animation ?: node.animation,
        speedVisuals = preset.speedVisuals ?: node.speedVisuals,
    )

    /** A ready-to-play page: tilt gravity, walls, a few objects and a spawn button. */
    fun sandboxPage(width: Float, height: Float): Page = Page(
        name = "Sandbox",
        world = WorldConfig(followTilt = true),
        widgets = listOf(
            node(spawner, width - 56f, 56f, 5),
            node(ball, width * 0.3f, height * 0.2f, 1),
            node(ball.copy(color = 0xFFDA4453), width * 0.5f, height * 0.15f, 2),
            node(ball.copy(color = 0xFF27AE60), width * 0.7f, height * 0.25f, 3),
            node(spinner, width / 2f, height * 0.55f, 4),
        ),
    )
}

package com.customlauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BodyType(val label: String) { NONE("None"), STATIC("Static"), DYNAMIC("Dynamic"), KINEMATIC("Kinematic") }

@Serializable
enum class ColliderShape(val label: String) { AUTO("Auto (from image)"), CIRCLE("Circle"), BOX("Box"), POLYGON("Custom polygon") }

/** Per-object physics. Positions stay in the node; the simulation moves them while it runs. */
@Serializable
data class PhysicsSpec(
    val type: BodyType = BodyType.DYNAMIC,
    /** Mass in "kg"; converted to density from the collider area. */
    val mass: Float = 1f,
    val friction: Float = 0.4f,
    /** Bounciness 0..1. */
    val restitution: Float = 0.5f,
    val linearDamping: Float = 0.2f,
    val angularDamping: Float = 0.2f,
    val gravityScale: Float = 1f,
    val collider: ColliderShape = ColliderShape.AUTO,
    /** Custom polygon in node fractions (0..1), max 8 convex points. */
    val polygon: List<PathPoint> = emptyList(),
    /** Pin the body to the page at (pinX, pinY) (node fractions): spinners, pendulums. */
    val pinned: Boolean = false,
    val pinX: Float = 0.5f,
    val pinY: Float = 0.5f,
    /** Keep the object upright (no rotation). */
    val fixedRotation: Boolean = false,
)

@Serializable
enum class GravityDirection(val label: String) { DOWN("Down"), UP("Up"), LEFT("Left"), RIGHT("Right"), NONE("Zero-g") }

/** Per-page physics world. */
@Serializable
data class WorldConfig(
    val gravity: GravityDirection = GravityDirection.DOWN,
    /** m/s² (Earth = 9.8). */
    val gravityStrength: Float = 9.8f,
    /** Gravity follows the device tilt (accelerometer). */
    val followTilt: Boolean = false,
    val wallLeft: Boolean = true,
    val wallRight: Boolean = true,
    val wallTop: Boolean = true,
    val wallBottom: Boolean = true,
    /** Widgets and icons without physics act as solid static obstacles. */
    val collideWithWidgets: Boolean = true,
)

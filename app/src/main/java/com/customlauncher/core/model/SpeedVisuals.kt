package com.customlauncher.core.model

import kotlinx.serialization.Serializable

/**
 * Speed-reactive rendering (fidget spinner / zoetrope effect). Spin speed in rpm:
 * below [blurFromRpm] the normal image; up to [framesFromRpm] a radial motion blur scaled by
 * speed; above it the sprite sheet [frames] plays with its frame tied to the rotation angle,
 * so the image appears to come alive like a zoetrope. Linear speed can stretch / blur /
 * trail the object. Everything is optional.
 */
@Serializable
data class SpeedVisuals(
    val motionBlur: Boolean = true,
    val blurFromRpm: Float = 120f,
    val framesFromRpm: Float = 600f,
    /** Sprite sheet from the sticker library for zoetrope mode (null = none). */
    val frames: String? = null,
    val frameCols: Int = 4,
    val frameRows: Int = 3,
    val stretchWhenFast: Boolean = true,
    val trails: Boolean = false,
    val speedLines: Boolean = false,
    val sparksOnCollision: Boolean = false,
    /** Haptic tick once per turn while spinning slowly (fidget spinner feel). */
    val tickPerTurn: Boolean = false,
)

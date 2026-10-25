package com.customlauncher.core.model

import kotlinx.serialization.Serializable

/**
 * Every input the gesture engine and the accessibility service can recognise.
 * Bindings are stored as `Map<Trigger, Action>` in [GestureConfig].
 */
@Serializable
enum class Trigger(val label: String, val group: TriggerGroup) {
    SWIPE_UP("Swipe up", TriggerGroup.GESTURE),
    SWIPE_DOWN("Swipe down", TriggerGroup.GESTURE),
    SWIPE_LEFT("Swipe left (single page only)", TriggerGroup.GESTURE),
    SWIPE_RIGHT("Swipe right (single page only)", TriggerGroup.GESTURE),
    TWO_FINGER_UP("Two-finger swipe up", TriggerGroup.GESTURE),
    TWO_FINGER_DOWN("Two-finger swipe down", TriggerGroup.GESTURE),
    TWO_FINGER_LEFT("Two-finger swipe left", TriggerGroup.GESTURE),
    TWO_FINGER_RIGHT("Two-finger swipe right", TriggerGroup.GESTURE),
    DOUBLE_TAP("Double tap", TriggerGroup.GESTURE),
    PINCH_IN("Pinch in", TriggerGroup.GESTURE),
    PINCH_OUT("Pinch out", TriggerGroup.GESTURE),

    CORNER_TOP_LEFT_TAP("Top-left corner: tap", TriggerGroup.CORNER),
    CORNER_TOP_LEFT_LONG("Top-left corner: long press", TriggerGroup.CORNER),
    CORNER_TOP_RIGHT_TAP("Top-right corner: tap", TriggerGroup.CORNER),
    CORNER_TOP_RIGHT_LONG("Top-right corner: long press", TriggerGroup.CORNER),
    CORNER_BOTTOM_LEFT_TAP("Bottom-left corner: tap", TriggerGroup.CORNER),
    CORNER_BOTTOM_LEFT_LONG("Bottom-left corner: long press", TriggerGroup.CORNER),
    CORNER_BOTTOM_RIGHT_TAP("Bottom-right corner: tap", TriggerGroup.CORNER),
    CORNER_BOTTOM_RIGHT_LONG("Bottom-right corner: long press", TriggerGroup.CORNER),

    EDGE_LEFT_SWIPE("Swipe in from left edge", TriggerGroup.EDGE),
    EDGE_RIGHT_SWIPE("Swipe in from right edge", TriggerGroup.EDGE),
    EDGE_TOP_SWIPE("Swipe in from top edge", TriggerGroup.EDGE),
    EDGE_BOTTOM_SWIPE("Swipe in from bottom edge", TriggerGroup.EDGE),

    VOLUME_UP_LONG("Volume up: long press", TriggerGroup.KEY),
    VOLUME_DOWN_LONG("Volume down: long press", TriggerGroup.KEY),
}

enum class TriggerGroup(val label: String) {
    GESTURE("Gestures"), CORNER("Hot corners"), EDGE("Edge swipes"), KEY("Hardware keys")
}

@Serializable
data class GestureConfig(
    val bindings: Map<Trigger, Action> = defaultBindings(),
    /** Single upper-case letter → action, used by the draw-a-letter canvas. */
    val letters: Map<String, Action> = mapOf(
        "S" to Action.OpenSettings,
        "M" to Action.MediaPlayPause,
    ),
    /** Size of the corner / edge hit zones in dp. */
    val cornerSizeDp: Float = 36f,
    val edgeSizeDp: Float = 18f,
) {
    fun actionFor(trigger: Trigger): Action = bindings[trigger] ?: Action.None

    companion object {
        fun defaultBindings(): Map<Trigger, Action> = mapOf(
            Trigger.SWIPE_UP to Action.OpenDrawer,
            Trigger.SWIPE_DOWN to Action.OpenSearch,
            Trigger.DOUBLE_TAP to Action.LockScreen,
            Trigger.PINCH_IN to Action.EnterEditMode,
            Trigger.TWO_FINGER_DOWN to Action.NotificationShade,
            Trigger.TWO_FINGER_UP to Action.DrawGesture,
        )
    }
}

package com.customlauncher.activities

import com.customlauncher.core.model.AutoSwitchRule

/** Current device conditions that rules are evaluated against. */
data class SwitchConditions(val minuteOfDay: Int, val charging: Boolean, val ssid: String?)

/** Pure rule evaluation, kept separate from the Android listeners so it is unit-testable. */
object AutoSwitchRules {
    /** First enabled rule that matches wins; returns its activity id. */
    fun evaluate(rules: List<AutoSwitchRule>, c: SwitchConditions): String? = rules.firstOrNull { rule ->
        rule.enabled && when (rule) {
            is AutoSwitchRule.TimeOfDay -> inRange(c.minuteOfDay, rule.startMinute, rule.endMinute)
            is AutoSwitchRule.Wifi -> c.ssid != null && c.ssid == rule.ssid.trim()
            is AutoSwitchRule.Charging -> c.charging == rule.charging
        }
    }?.activityId

    /** Minute-of-day range check; ranges may wrap past midnight. */
    fun inRange(minute: Int, start: Int, end: Int): Boolean =
        if (start <= end) minute in start until end else minute >= start || minute < end
}

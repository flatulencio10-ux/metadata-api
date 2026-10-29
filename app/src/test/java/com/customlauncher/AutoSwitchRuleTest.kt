package com.customlauncher

import com.customlauncher.activities.AutoSwitchRules.evaluate
import com.customlauncher.activities.AutoSwitchRules.inRange
import com.customlauncher.activities.SwitchConditions
import com.customlauncher.core.model.AutoSwitchRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSwitchRuleTest {
    @Test fun wrappingTimeRange() {
        assertTrue(inRange(23 * 60, 22 * 60, 6 * 60))
        assertTrue(inRange(5 * 60, 22 * 60, 6 * 60))
        assertFalse(inRange(12 * 60, 22 * 60, 6 * 60))
    }

    @Test fun firstMatchingRuleWins() {
        val rules = listOf(
            AutoSwitchRule.Wifi("school", "SchoolNet"),
            AutoSwitchRule.Charging("charging", charging = true),
            AutoSwitchRule.TimeOfDay("night", 22 * 60, 6 * 60),
        )
        assertEquals("school", evaluate(rules, SwitchConditions(23 * 60, true, "SchoolNet")))
        assertEquals("charging", evaluate(rules, SwitchConditions(23 * 60, true, null)))
        assertEquals("night", evaluate(rules, SwitchConditions(23 * 60, false, "Other")))
        assertNull(evaluate(rules, SwitchConditions(12 * 60, false, null)))
    }
}

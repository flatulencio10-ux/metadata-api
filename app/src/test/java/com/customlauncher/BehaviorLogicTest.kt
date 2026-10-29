package com.customlauncher

import com.customlauncher.core.model.BCondition
import com.customlauncher.core.model.CompareOp
import com.customlauncher.core.model.VarOp
import com.customlauncher.core.model.VarScope
import com.customlauncher.objects.BehaviorLogic
import com.customlauncher.objects.VariableStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorLogicTest {
    @Test fun tapCounterReachesTen() {
        val vars = VariableStore()
        repeat(10) { vars.set(VarScope.OBJECT, "n1", "taps", VarOp.ADD, 1.0) }
        val cond = listOf(BCondition.Variable(VarScope.OBJECT, "taps", CompareOp.GE, 10.0))
        assertTrue(BehaviorLogic.conditionsHold(cond, "n1", "idle", vars))
        assertFalse(BehaviorLogic.conditionsHold(cond, "n2", "idle", vars))
    }

    @Test fun seedDoesNotOverwrite() {
        val vars = VariableStore()
        vars.set(VarScope.GLOBAL, "", "score", VarOp.SET, 5.0)
        vars.seed(VarScope.GLOBAL, "", mapOf("score" to 0.0, "lives" to 3.0))
        assertEquals(5.0, vars.get(VarScope.GLOBAL, "", "score"), 0.0)
        assertEquals(3.0, vars.get(VarScope.GLOBAL, "", "lives"), 0.0)
    }

    @Test fun stateCondition() {
        assertTrue(BehaviorLogic.conditionsHold(listOf(BCondition.StateIs("spinning")), "n", "spinning", VariableStore()))
    }

    @Test fun thresholdCrossing() {
        assertTrue(BehaviorLogic.crossed(480f, 520f, 500f, upward = true))
        assertFalse(BehaviorLogic.crossed(520f, 540f, 500f, upward = true))
        assertTrue(BehaviorLogic.crossed(520f, 480f, 500f, upward = false))
    }
}

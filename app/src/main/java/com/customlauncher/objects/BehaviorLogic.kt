package com.customlauncher.objects

import com.customlauncher.core.model.BCondition
import com.customlauncher.core.model.CompareOp
import com.customlauncher.core.model.VarOp
import com.customlauncher.core.model.VarScope
import kotlin.random.Random

/**
 * Behaviour variables: global ones and per-object ones ("tap count"…). Values are numbers;
 * booleans are 0/1. Framework-free; persistence is plugged in via [onChange].
 */
class VariableStore(initial: Map<String, Double> = emptyMap(), private val onChange: (Map<String, Double>) -> Unit = {}) {
    private val values = HashMap(initial)

    private fun key(scope: VarScope, nodeId: String, name: String) =
        if (scope == VarScope.GLOBAL) "g:$name" else "o:$nodeId:$name"

    fun get(scope: VarScope, nodeId: String, name: String, default: Double = 0.0): Double =
        values[key(scope, nodeId, name)] ?: default

    fun set(scope: VarScope, nodeId: String, name: String, op: VarOp, value: Double): Double {
        val k = key(scope, nodeId, name)
        val v = if (op == VarOp.ADD) (values[k] ?: 0.0) + value else value
        values[k] = v
        onChange(HashMap(values))
        return v
    }

    /** Seeds initial values without overwriting values already stored. */
    fun seed(scope: VarScope, nodeId: String, initial: Map<String, Double>) {
        var changed = false
        initial.forEach { (name, v) -> if (values.putIfAbsent(key(scope, nodeId, name), v) == null) changed = true }
        if (changed) onChange(HashMap(values))
    }

    fun snapshot(): Map<String, Double> = HashMap(values)
}

object BehaviorLogic {
    fun compare(a: Double, op: CompareOp, b: Double): Boolean = when (op) {
        CompareOp.EQ -> a == b
        CompareOp.NE -> a != b
        CompareOp.LT -> a < b
        CompareOp.LE -> a <= b
        CompareOp.GT -> a > b
        CompareOp.GE -> a >= b
    }

    fun conditionsHold(
        conditions: List<BCondition>,
        nodeId: String,
        state: String,
        vars: VariableStore,
        random: Random = Random.Default,
    ): Boolean = conditions.all { c ->
        when (c) {
            is BCondition.Variable -> compare(vars.get(c.scope, nodeId, c.name), c.op, c.value)
            is BCondition.StateIs -> state == c.state
            is BCondition.Chance -> random.nextFloat() * 100f < c.percent
        }
    }

    /** True when [value] crossed [threshold] between two samples, in the given direction. */
    fun crossed(previous: Float, value: Float, threshold: Float, upward: Boolean): Boolean =
        if (upward) previous < threshold && value >= threshold else previous > threshold && value <= threshold
}

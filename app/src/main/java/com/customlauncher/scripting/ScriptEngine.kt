package com.customlauncher.scripting

import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * What a script may do to the launcher. Implemented on Android by [ScriptExtension] and by a
 * fake in unit tests, so the engine itself stays framework-free.
 */
interface ScriptBridge {
    /** x, y, rotation, scale, rpm, vx, vy of a node. */
    fun get(nodeId: String, prop: String): Double
    fun set(nodeId: String, prop: String, value: Double)
    fun state(nodeId: String): String
    fun setState(nodeId: String, state: String)
    /** Object actions: impulse, spin, image, play, emit, haptic, say, sound, moveTo. */
    fun action(nodeId: String, name: String, args: List<Any?>)
    fun getVar(global: Boolean, nodeId: String, name: String): Double?
    fun setVar(global: Boolean, nodeId: String, name: String, value: Double)
    fun openApp(packageName: String): Boolean
    fun fetch(url: String, done: (error: String?, body: String?) -> Unit)
    fun after(ms: Long, block: () -> Unit)
    fun broadcast(fromId: String, name: String, data: Any?)
    fun log(nodeId: String, error: Boolean, message: String)
}

/** Thrown from the instruction observer; extends Error so JS try/catch cannot swallow it. */
class ScriptTimeout : Error("Script used too much CPU (infinite loop?)")

/**
 * Sandboxed Rhino context: interpreted mode, ES6, no Java access (every class is shut out
 * and only the safe standard objects exist), and an instruction budget per hook call.
 */
internal object SandboxFactory : ContextFactory() {
    const val BUDGET = 2_000_000
    private val remaining = ThreadLocal<IntArray>()
    private val depth = ThreadLocal<IntArray>()

    override fun makeContext(): Context = super.makeContext().apply {
        @Suppress("DEPRECATION")
        optimizationLevel = -1
        languageVersion = Context.VERSION_ES6
        instructionObserverThreshold = 10_000
        setClassShutter { false }
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val left = remaining.get() ?: return
        left[0] -= instructionCount
        if (left[0] < 0) throw ScriptTimeout()
    }

    /** Runs [block] in an entered context; the outermost call gets a fresh CPU budget. */
    fun <T> run(budget: Int = BUDGET, block: (Context) -> T): T {
        val d = depth.get() ?: IntArray(1).also { depth.set(it) }
        if (d[0] == 0) remaining.set(intArrayOf(budget))
        d[0]++
        val cx = enterContext()
        try {
            return block(cx)
        } finally {
            Context.exit()
            d[0]--
        }
    }
}

/**
 * One object's script. Hooks are plain global functions (onTap, onSpin, onCollide, onTick,
 * onState, onNotification, onMessage, onDragStart, onDragEnd, onLongPress, onDoubleTap)
 * called with `this` bound to the object. A hook that throws or loops forever disables the
 * script and reports through [onCrash].
 */
class ScriptInstance(
    val nodeId: String,
    source: String,
    private val bridge: ScriptBridge,
    private val onCrash: (String) -> Unit = {},
) {
    var disabled = false
        private set
    var crash: String? = null
        private set

    private lateinit var scope: ScriptableObject
    private lateinit var self: Scriptable

    init {
        guard("load") { cx ->
            scope = cx.initSafeStandardObjects()
            self = SelfObject(scope)
            ScriptableObject.putProperty(scope, "self", self)
            ScriptableObject.putProperty(scope, "launcher", launcherObject(scope))
            fn(scope, scope, "log", 1) { _, a -> bridge.log(nodeId, false, a.joinToString(" ") { Context.toString(it) }); Undefined.instance }
            fn(scope, scope, "print", 1) { _, a -> bridge.log(nodeId, false, a.joinToString(" ") { Context.toString(it) }); Undefined.instance }
            fn(scope, scope, "fetch", 2) { _, a ->
                val url = Context.toString(a.getOrNull(0))
                val cb = a.getOrNull(1) as? Function
                if (!url.startsWith("https://")) callback(cb, "Only https:// URLs are allowed", null)
                else bridge.fetch(url) { err, body -> callback(cb, err, body) }
                Undefined.instance
            }
            fn(scope, scope, "after", 2) { _, a ->
                val ms = Context.toNumber(a.getOrNull(0)).toLong().coerceAtLeast(0)
                val cb = a.getOrNull(1) as? Function
                if (cb != null) bridge.after(ms) { invoke("after") { cx2 -> cb.call(cx2, scope, self, emptyArray()) } }
                Undefined.instance
            }
            cx.evaluateString(scope, source, "object.js", 1, null)
        }
    }

    fun has(hook: String): Boolean = !disabled && ::scope.isInitialized && scope.get(hook, scope) is Function

    /** Calls [hook] if the script defines it. Returns false when it isn't defined or failed. */
    fun call(hook: String, vararg args: Any?): Boolean {
        if (!has(hook)) return false
        val f = scope.get(hook, scope) as Function
        return invoke(hook) { cx -> f.call(cx, scope, self, args.map { toJs(cx, it) }.toTypedArray()) }
    }

    private fun callback(cb: Function?, err: String?, body: String?) {
        if (cb == null || disabled) return
        invoke("fetch callback") { cx -> cb.call(cx, scope, self, arrayOf(err, body)) }
    }

    private fun invoke(what: String, block: (Context) -> Any?): Boolean {
        if (disabled) return false
        return guard(what, block)
    }

    private fun guard(what: String, block: (Context) -> Any?): Boolean = try {
        SandboxFactory.run { block(it) }
        true
    } catch (e: ScriptTimeout) {
        fail("$what: ${e.message}")
    } catch (e: RhinoException) {
        fail("$what: line ${e.lineNumber()}: ${e.details()}")
    } catch (e: Exception) {
        fail("$what: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: StackOverflowError) {
        fail("$what: too much recursion")
    }

    private fun fail(message: String): Boolean {
        disabled = true
        crash = message
        bridge.log(nodeId, true, message)
        onCrash(message)
        return false
    }

    private fun toJs(cx: Context, v: Any?): Any? = when (v) {
        null -> null
        is Number -> v.toDouble()
        is Map<*, *> -> (cx.newObject(scope) as NativeObject).also { o -> v.forEach { (k, x) -> o.put(k.toString(), o, toJs(cx, x)) } }
        else -> v.toString()
    }

    private fun fn(scope: Scriptable, target: ScriptableObject, name: String, arity: Int, body: (Scriptable, Array<Any?>) -> Any?) {
        val callable = Callable { _, _, thisObj, args -> body(thisObj, args.map { if (it === Undefined.instance) null else it }.toTypedArray()) }
        target.defineProperty(name, LambdaFunction(scope, name, arity, callable), ScriptableObject.DONTENUM)
    }

    private fun launcherObject(scope: ScriptableObject): Scriptable {
        val o = NativeObject().also { it.parentScope = scope; it.prototype = ScriptableObject.getObjectPrototype(scope) }
        fn(scope, o, "openApp", 1) { _, a -> bridge.openApp(Context.toString(a.getOrNull(0))) }
        fn(scope, o, "broadcast", 2) { _, a -> bridge.broadcast(nodeId, Context.toString(a.getOrNull(0)), a.getOrNull(1)?.let { Context.toString(it) }); Undefined.instance }
        o.defineProperty("vars", VarsObject(scope, global = true), ScriptableObject.READONLY)
        return o
    }

    /** `this` inside hooks: live properties backed by the bridge, plus action methods. */
    private inner class SelfObject(scope: ScriptableObject) : ScriptableObject(scope, getObjectPrototype(scope)) {
        private val numeric = setOf("x", "y", "rotation", "scale", "rpm", "vx", "vy")

        init {
            fun act(name: String, arity: Int, action: String = name) = fn(scope, this, name, arity) { _, a -> bridge.action(nodeId, action, a.map(::plain)); Undefined.instance }
            act("applyImpulse", 2, "impulse")
            act("spin", 1)
            act("setImage", 1, "image")
            act("play", 1)
            act("emit", 2)
            act("haptic", 1)
            act("say", 2)
            act("sound", 1)
            act("moveTo", 3)
            defineProperty("vars", VarsObject(scope, global = false), READONLY)
        }

        override fun getClassName() = "LauncherObject"

        override fun get(name: String, start: Scriptable): Any? = when (name) {
            in numeric -> bridge.get(nodeId, name)
            "id" -> nodeId
            "state" -> bridge.state(nodeId)
            else -> super.get(name, start)
        }

        override fun has(name: String, start: Scriptable): Boolean = name in numeric || name == "id" || name == "state" || super.has(name, start)

        override fun put(name: String, start: Scriptable, value: Any?) {
            when (name) {
                in numeric -> bridge.set(nodeId, name, Context.toNumber(value))
                "state" -> bridge.setState(nodeId, Context.toString(value))
                else -> super.put(name, start, value)
            }
        }
    }

    /** Numeric variables: `this.vars` (per object) and `launcher.vars` (global). */
    private inner class VarsObject(scope: ScriptableObject, private val global: Boolean) : ScriptableObject(scope, getObjectPrototype(scope)) {
        override fun getClassName() = "Variables"
        override fun get(name: String, start: Scriptable): Any? =
            bridge.getVar(global, nodeId, name) ?: super.get(name, start).takeIf { it !== NOT_FOUND } ?: Undefined.instance
        override fun has(name: String, start: Scriptable) = bridge.getVar(global, nodeId, name) != null || super.has(name, start)
        override fun put(name: String, start: Scriptable, value: Any?) = bridge.setVar(global, nodeId, name, Context.toNumber(value))
    }

    private fun plain(v: Any?): Any? = when (v) {
        null, Undefined.instance -> null
        is Number -> v.toDouble()
        is Boolean -> v
        else -> Context.toString(v)
    }
}

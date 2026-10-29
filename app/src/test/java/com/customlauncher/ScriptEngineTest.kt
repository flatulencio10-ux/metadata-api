package com.customlauncher

import com.customlauncher.scripting.ScriptBridge
import com.customlauncher.scripting.ScriptExamples
import com.customlauncher.scripting.ScriptInstance
import com.customlauncher.scripting.ScriptSyntax
import com.customlauncher.scripting.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptEngineTest {
    private class FakeBridge : ScriptBridge {
        val props = HashMap<String, Double>()
        val vars = HashMap<String, Double>()
        val actions = ArrayList<Pair<String, List<Any?>>>()
        val opened = ArrayList<String>()
        val logs = ArrayList<String>()
        var state = "idle"
        override fun get(nodeId: String, prop: String) = props[prop] ?: 0.0
        override fun set(nodeId: String, prop: String, value: Double) { props[prop] = value }
        override fun state(nodeId: String) = state
        override fun setState(nodeId: String, state: String) { this.state = state }
        override fun action(nodeId: String, name: String, args: List<Any?>) { actions += name to args }
        override fun getVar(global: Boolean, nodeId: String, name: String) = vars["$global:$name"]
        override fun setVar(global: Boolean, nodeId: String, name: String, value: Double) { vars["$global:$name"] = value }
        override fun openApp(packageName: String): Boolean { opened += packageName; return true }
        override fun fetch(url: String, done: (String?, String?) -> Unit) = done(null, "{\"ok\":true}")
        override fun after(ms: Long, block: () -> Unit) = block()
        override fun broadcast(fromId: String, name: String, data: Any?) = Unit
        override fun log(nodeId: String, error: Boolean, message: String) { logs += message }
    }

    @Test fun tapCounterSwapsImageAtTen() {
        val b = FakeBridge()
        val s = ScriptInstance("n", ScriptExamples.TAP_COUNTER, b)
        repeat(10) { assertTrue(s.call("onTap", mapOf("type" to "tap"))) }
        assertEquals(10.0, b.vars["false:taps"])
        assertTrue(b.actions.any { it.first == "image" && it.second == listOf("trophy") })
    }

    @Test fun spinnerOpensSpotifyOnce() {
        val b = FakeBridge()
        val s = ScriptInstance("n", ScriptExamples.SPOTIFY_SPINNER, b)
        s.call("onSpin", 300.0); s.call("onSpin", 600.0); s.call("onSpin", 700.0)
        assertEquals(listOf("com.spotify.music"), b.opened)
        s.call("onSpin", 50.0); s.call("onSpin", 800.0)
        assertEquals(2, b.opened.size)
    }

    @Test fun whatsappBounce() {
        val b = FakeBridge()
        val s = ScriptInstance("n", ScriptExamples.WHATSAPP_BOUNCE, b)
        s.call("onNotification", "com.other")
        assertTrue(b.actions.isEmpty())
        s.call("onNotification", "com.whatsapp")
        assertEquals("impulse" to listOf<Any?>(0.0, -900.0), b.actions.first())
    }

    @Test fun propertiesAndState() {
        val b = FakeBridge().apply { props["x"] = 10.0 }
        val s = ScriptInstance("n", "function onTick(dt) { this.x = this.x + dt; if (this.x > 15) this.state = 'far'; }", b)
        s.call("onTick", 10.0)
        assertEquals(20.0, b.props["x"]!!, 0.0)
        assertEquals("far", b.state)
    }

    @Test fun infiniteLoopIsStoppedAndDisabled() {
        val b = FakeBridge()
        val s = ScriptInstance("n", "function onTap() { while (true) {} }", b)
        assertFalse(s.call("onTap"))
        assertTrue(s.disabled)
        assertFalse(s.call("onTap"))
        assertTrue(b.logs.single().contains("CPU"))
    }

    @Test fun noJavaAccess() {
        val b = FakeBridge()
        val s = ScriptInstance("n", "function onTap() { log(typeof java, typeof Packages); java.lang.System.exit(1); }", b)
        assertFalse(s.call("onTap"))
        assertEquals("undefined undefined", b.logs.first())
    }

    @Test fun syntaxErrorReportsLine() {
        val b = FakeBridge()
        val s = ScriptInstance("n", "function onTap() {\n  var = 3;\n}", b)
        assertTrue(s.disabled)
        assertTrue(b.logs.single(), b.logs.single().contains("line 2"))
    }

    @Test fun fetchCallback() {
        val b = FakeBridge()
        ScriptInstance("n", "function onTap() { fetch('https://x.test', function(err, body) { log(JSON.parse(body).ok); }); }", b).call("onTap")
        assertEquals("true", b.logs.single())
    }

    @Test fun tokenizer() {
        val code = "// hi\nfunction onTap(e) { this.say(\"x\", 12); }"
        val kinds = ScriptSyntax.tokenize(code).map { code.substring(it.start, it.end) to it.kind }
        assertTrue(kinds.contains("// hi" to TokenKind.COMMENT))
        assertTrue(kinds.contains("function" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("onTap" to TokenKind.HOOK))
        assertTrue(kinds.contains("say" to TokenKind.API))
        assertTrue(kinds.contains("\"x\"" to TokenKind.STRING))
        assertTrue(kinds.contains("12" to TokenKind.NUMBER))
    }
}

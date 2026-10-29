package com.customlauncher.ui.scripting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.container
import com.customlauncher.core.model.Node
import com.customlauncher.scripting.ScriptBridge
import com.customlauncher.scripting.ScriptExamples
import com.customlauncher.scripting.ScriptInstance
import com.customlauncher.scripting.ScriptSyntax
import com.customlauncher.scripting.TokenKind
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.stickers.Hint
import com.customlauncher.ui.stickers.ToolPill

private val CodeBg = Color(0xFF101218)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFE6E8EF))

private fun colorOf(kind: TokenKind) = when (kind) {
    TokenKind.KEYWORD -> SpanStyle(color = Color(0xFFC792EA))
    TokenKind.STRING -> SpanStyle(color = Color(0xFFC3E88D))
    TokenKind.NUMBER -> SpanStyle(color = Color(0xFFF78C6C))
    TokenKind.COMMENT -> SpanStyle(color = Color(0xFF6B7385), fontStyle = FontStyle.Italic)
    TokenKind.API -> SpanStyle(color = Color(0xFF82AAFF))
    TokenKind.HOOK -> SpanStyle(color = Color(0xFFFFCB6B))
}

/** Colours JavaScript as you type (same text, so offsets map 1:1). */
private object JsHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text.text).apply {
            ScriptSyntax.tokenize(text.text).forEach { addStyle(colorOf(it.kind), it.start, it.end) }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Compiles without touching the launcher, to surface syntax errors before saving. */
private object DryBridge : ScriptBridge {
    override fun get(nodeId: String, prop: String) = 0.0
    override fun set(nodeId: String, prop: String, value: Double) = Unit
    override fun state(nodeId: String) = "idle"
    override fun setState(nodeId: String, state: String) = Unit
    override fun action(nodeId: String, name: String, args: List<Any?>) = Unit
    override fun getVar(global: Boolean, nodeId: String, name: String): Double? = null
    override fun setVar(global: Boolean, nodeId: String, name: String, value: Double) = Unit
    override fun openApp(packageName: String) = false
    override fun fetch(url: String, done: (String?, String?) -> Unit) = Unit
    override fun after(ms: Long, block: () -> Unit) = Unit
    override fun broadcast(fromId: String, name: String, data: Any?) = Unit
    override fun log(nodeId: String, error: Boolean, message: String) = Unit
}

/**
 * Code editor for an object's script: syntax highlighting, line numbers, examples, a syntax
 * check on save and the script's console. Saved scripts reload automatically the next time
 * the page's session starts (leaving Edit Mode, page shown).
 */
@Composable
fun ScriptEditor(node: Node, vm: LauncherViewModel) {
    val c = LocalContext.current.container
    val haptics = LocalHapticFeedback.current
    var draft by remember(node.id) { mutableStateOf(node.script ?: "") }
    var check by remember(node.id) { mutableStateOf<String?>(null) }
    val dirty = draft != (node.script ?: "")

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScriptExamples.all.forEach { (name, code) ->
            ToolPill(Icons.Filled.Code, name, selected = false) { draft = code; check = null }
        }
    }
    val lines = draft.count { it == '\n' } + 1
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CodeBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            (1..lines).joinToString("\n"), style = Mono.copy(color = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.replace("\t", "  "); check = null },
            textStyle = Mono,
            cursorBrush = SolidColor(Color(0xFF7C9CFF)),
            visualTransformation = JsHighlighter,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
    }

    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolPill(Icons.Filled.Check, "Check", selected = false) {
            val probe = ScriptInstance(node.id, draft, DryBridge)
            check = probe.crash ?: "No errors. Hooks: " + ScriptSyntax.hooks.filter { probe.has(it) }.ifEmpty { listOf("none") }.joinToString()
        }
        ToolPill(Icons.Filled.PlayArrow, if (dirty) "Save & reload" else "Reload", selected = false, highlight = dirty) {
            val probe = ScriptInstance(node.id, draft, DryBridge)
            if (probe.crash != null) {
                check = probe.crash
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                return@ToolPill
            }
            c.scriptConsole.clear(node.id)
            vm.updateWidget(node.id) { it.copy(script = draft.takeIf { s -> s.isNotBlank() }) }
            vm.scriptGeneration++
            check = "Saved — runs when you leave Edit Mode."
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (dirty) ToolPill(Icons.Filled.Undo, "Revert", selected = false) { draft = node.script ?: ""; check = null }
        if (node.script != null) ToolPill(Icons.Filled.Delete, "Remove", selected = false) {
            draft = ""
            vm.updateWidget(node.id) { it.copy(script = null) }
        }
    }
    check?.let {
        val ok = it.startsWith("No errors") || it.startsWith("Saved")
        Text(it, color = if (ok) Color(0xFF8BE28B) else Color(0xFFFF7A7A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }

    c.scriptConsole.crashed[node.id]?.let {
        Text("⚠ Disabled after an error: $it — fix it and Save & reload.", color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
    Text("Console", color = Color(0xFF7C9CFF), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    val output = c.scriptConsole.of(node.id)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBg)
            .padding(8.dp)
    ) {
        if (output.isEmpty()) Text("log(…) output and errors appear here.", style = Mono.copy(color = Color.White.copy(alpha = 0.4f)))
        output.takeLast(30).forEach { line ->
            Text(line.text, style = Mono.copy(color = if (line.error) Color(0xFFFF7A7A) else Color(0xFFE6E8EF), fontSize = 12.sp))
        }
    }
    Spacer(Modifier.height(6.dp))
    Hint("Hooks: onTap(e), onDoubleTap, onLongPress, onDragStart, onDragEnd(e), onSpin(rpm), onCollide(otherId, impulse), onTick(dt), onState(name), onNotification(pkg), onMessage(name, data, fromId). " +
        "`this`: x, y, rotation, scale, rpm, vx, vy, state, vars, applyImpulse(dx,dy), spin(rpm), setImage(name), play(clip), emit(count, color), haptic(pattern), say(text, ms), sound(src), moveTo(x, y, ms). " +
        "Globals: launcher.openApp(pkg), launcher.vars, launcher.broadcast(name, data), fetch(url, fn(err, body)), after(ms, fn), log(…).")
    Spacer(Modifier.width(1.dp))
}

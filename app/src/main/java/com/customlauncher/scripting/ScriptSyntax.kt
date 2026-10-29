package com.customlauncher.scripting

/** Token kinds for the script editor's syntax highlighting. */
enum class TokenKind { KEYWORD, STRING, NUMBER, COMMENT, API, HOOK }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

/** A small, forgiving JavaScript tokenizer (framework-free; unit tested). */
object ScriptSyntax {
    private val keywords = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "break",
        "continue", "new", "this", "true", "false", "null", "undefined", "typeof", "in", "of",
        "switch", "case", "default", "try", "catch", "finally", "throw",
    )
    val hooks = listOf("onTap", "onDoubleTap", "onLongPress", "onDragStart", "onDragEnd", "onSpin", "onCollide", "onTick", "onState", "onNotification", "onMessage")
    private val api = setOf(
        "launcher", "self", "log", "print", "fetch", "after", "applyImpulse", "spin", "setImage", "play", "emit",
        "haptic", "say", "sound", "moveTo", "openApp", "broadcast", "vars", "Math",
    )

    fun tokenize(code: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '/' && code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    out += Token(i, end, TokenKind.COMMENT); i = end
                }
                c == '/' && code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                    out += Token(i, end, TokenKind.COMMENT); i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != c && code[j] != '\n') j += if (code[j] == '\\') 2 else 1
                    val end = (j + 1).coerceAtMost(code.length)
                    out += Token(i, end, TokenKind.STRING); i = end
                }
                c.isDigit() -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.')) j++
                    out += Token(i, j, TokenKind.NUMBER); i = j
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) j++
                    val word = code.substring(i, j)
                    when (word) {
                        in keywords -> out += Token(i, j, TokenKind.KEYWORD)
                        in hooks -> out += Token(i, j, TokenKind.HOOK)
                        in api -> out += Token(i, j, TokenKind.API)
                    }
                    i = j
                }
                else -> i++
            }
        }
        return out
    }
}

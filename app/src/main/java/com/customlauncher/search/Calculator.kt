package com.customlauncher.search

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Small recursive-descent expression evaluator for inline search results:
 * `+ - * / % ^`, parentheses, unary minus, decimals, constants (pi, e) and
 * functions (sqrt, sin, cos, tan, ln, log). Operator precedence follows math rules,
 * so `2+2*3` = 8. Framework-free and unit tested.
 */
object Calculator {

    /** Returns null when [input] is not a calculable expression (e.g. plain words or a single number). */
    fun evaluate(input: String): Double? {
        val text = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        if (text.isEmpty()) return null
        // Require at least one operator or function so typing "42" doesn't show a result.
        if (text.none { it in "+-*/%^(" } && text.lowercase() !in setOf("pi", "e")) return null
        return try {
            val parser = Parser(text)
            val value = parser.parseExpression()
            if (!parser.atEnd()) null else value.takeIf { !it.isNaN() && !it.isInfinite() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Formats without a trailing ".0" and with up to 10 significant decimals. */
    fun format(value: Double): String {
        if (abs(value - Math.rint(value)) < 1e-10 && abs(value) < 1e15) return value.toLong().toString()
        return "%.10f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        private fun peek(): Char? = s.getOrNull(pos)

        private fun eat(c: Char): Boolean {
            if (peek() == c) { pos++; return true }
            return false
        }

        // expression := term (('+'|'-') term)*
        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                v = when {
                    eat('+') -> v + parseTerm()
                    eat('-') -> v - parseTerm()
                    else -> return v
                }
            }
        }

        // term := factor (('*'|'/'|'%') factor)*
        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                v = when {
                    eat('*') -> v * parseFactor()
                    eat('/') -> v / parseFactor()
                    eat('%') -> v % parseFactor()
                    else -> return v
                }
            }
        }

        // factor := unary ('^' factor)?   (right associative)
        private fun parseFactor(): Double {
            val base = parseUnary()
            return if (eat('^')) base.pow(parseFactor()) else base
        }

        private fun parseUnary(): Double = when {
            eat('-') -> -parseUnary()
            eat('+') -> parseUnary()
            else -> parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (eat('(')) {
                val v = parseExpression()
                require(eat(')')) { "missing )" }
                return v
            }
            val c = peek() ?: throw IllegalArgumentException("unexpected end")
            if (c.isDigit() || c == '.') {
                val start = pos
                while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
                return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
            }
            if (c.isLetter()) {
                val start = pos
                while (peek()?.isLetter() == true) pos++
                val name = s.substring(start, pos).lowercase()
                when (name) {
                    "pi" -> return PI
                    "e" -> return E
                }
                require(eat('(')) { "expected (" }
                val arg = parseExpression()
                require(eat(')')) { "missing )" }
                return when (name) {
                    "sqrt" -> sqrt(arg)
                    "sin" -> sin(arg)
                    "cos" -> cos(arg)
                    "tan" -> tan(arg)
                    "ln" -> ln(arg)
                    "log" -> log10(arg)
                    "abs" -> abs(arg)
                    else -> throw IllegalArgumentException("unknown function $name")
                }
            }
            throw IllegalArgumentException("unexpected '$c'")
        }
    }
}

package com.xzo.agent.util

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Small, dependency-free, safe math expression evaluator.
 * Supports: + - * / % ^, unary minus, parentheses, constants (pi, e),
 * functions (sin cos tan asin acos atan sqrt cbrt ln log exp abs floor ceil round
 * min max pow hypot fact), and comma-separated arguments.
 *
 * Used by the `calculator` tool and as the offline path for `code_execution`.
 */
object Calc {

    class CalcError(message: String) : IllegalArgumentException(message)

    fun eval(expression: String): Double {
        val p = Parser(expression.lowercase().replace("×", "*").replace("÷", "/").replace(",", ","))
        val v = p.parseExpression()
        p.skipWs()
        if (!p.atEnd()) throw CalcError("Unexpected '${p.peek()}' at ${p.pos}")
        return v
    }

    fun evalToString(expression: String): String {
        val v = eval(expression)
        return format(v)
    }

    fun format(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
        return if (abs(v - round(v)) < 1e-10 && abs(v) < 1e15) {
            round(v).toLong().toString()
        } else {
            val s = "%.10f".format(v).trimEnd('0').trimEnd('.')
            if (s.isEmpty() || s == "-") "0" else s
        }
    }

    private class Parser(val src: String) {
        var pos = 0

        fun atEnd() = pos >= src.length
        fun peek(): Char = if (atEnd()) '\u0000' else src[pos]
        fun skipWs() { while (!atEnd() && src[pos].isWhitespace()) pos++ }

        fun parseExpression(): Double {
            var left = parseTerm()
            while (true) {
                skipWs()
                when (peek()) {
                    '+' -> { pos++; left += parseTerm() }
                    '-' -> { pos++; left -= parseTerm() }
                    else -> return left
                }
            }
        }

        fun parseTerm(): Double {
            var left = parseUnary()
            while (true) {
                skipWs()
                when (peek()) {
                    '*' -> { pos++; left *= parseUnary() }
                    '/' -> {
                        pos++
                        val d = parseUnary()
                        if (d == 0.0) throw CalcError("Division by zero")
                        left /= d
                    }
                    '%' -> {
                        pos++
                        val d = parseUnary()
                        if (d == 0.0) throw CalcError("Modulo by zero")
                        left %= d
                    }
                    else -> return left
                }
            }
        }

        fun parseUnary(): Double {
            skipWs()
            return when (peek()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> parsePower()
            }
        }

        fun parsePower(): Double {
            val base = parseAtom()
            skipWs()
            if (peek() == '^' || (peek() == '*' && pos + 1 < src.length && src[pos + 1] == '*')) {
                if (peek() == '^') pos++ else pos += 2
                val e = parseUnary()
                return base.pow(e)
            }
            return base
        }

        fun parseAtom(): Double {
            skipWs()
            if (atEnd()) throw CalcError("Unexpected end of expression")
            val c = peek()
            if (c == '(') {
                pos++
                val v = parseExpression()
                skipWs()
                if (peek() != ')') throw CalcError("Missing ')'")
                pos++
                return v
            }
            if (c.isDigit() || c == '.') return parseNumber()
            if (c.isLetter() || c == '_') return parseIdentifier()
            throw CalcError("Unexpected character '$c'")
        }

        fun parseNumber(): Double {
            val start = pos
            while (!atEnd() && (src[pos].isDigit() || src[pos] == '.' || src[pos] == '_')) pos++
            if (!atEnd() && (src[pos] == 'e') &&
                pos + 1 < src.length && (src[pos + 1].isDigit() || src[pos + 1] == '-' || src[pos + 1] == '+')
            ) {
                pos++
                if (src[pos] == '-' || src[pos] == '+') pos++
                while (!atEnd() && src[pos].isDigit()) pos++
            }
            val raw = src.substring(start, pos).replace("_", "")
            return raw.toDoubleOrNull() ?: throw CalcError("Bad number '$raw'")
        }

        fun parseIdentifier(): Double {
            val start = pos
            while (!atEnd() && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            val name = src.substring(start, pos)
            skipWs()
            if (peek() == '(') {
                pos++
                val args = mutableListOf<Double>()
                skipWs()
                if (peek() == ')') { pos++ } else {
                    while (true) {
                        args += parseExpression()
                        skipWs()
                        when (peek()) {
                            ',' -> pos++
                            ')' -> { pos++; break }
                            else -> throw CalcError("Expected ',' or ')' in $name(...)")
                        }
                    }
                }
                return applyFunction(name, args)
            }
            return when (name) {
                "pi", "π" -> Math.PI
                "e" -> Math.E
                "tau" -> Math.PI * 2
                "phi" -> (1 + sqrt(5.0)) / 2
                else -> throw CalcError("Unknown constant '$name'")
            }
        }

        fun applyFunction(name: String, a: List<Double>): Double {
            fun one(): Double = a.getOrNull(0) ?: throw CalcError("$name needs 1 argument")
            return when (name) {
                "sin" -> sin(one()); "cos" -> cos(one()); "tan" -> tan(one())
                "asin" -> asin(one()); "acos" -> acos(one()); "atan" -> atan(one())
                "sqrt" -> sqrt(one()); "cbrt" -> cbrt(one())
                "ln" -> ln(one()); "log" -> if (a.size == 2) ln(a[1]) / ln(a[0]) else log10(one())
                "log2" -> ln(one()) / ln(2.0)
                "exp" -> exp(one()); "abs" -> abs(one())
                "floor" -> floor(one()); "ceil" -> ceil(one()); "round" -> round(one())
                "sign" -> kotlin.math.sign(one())
                "min" -> a.minOrNull() ?: throw CalcError("min needs arguments")
                "max" -> a.maxOrNull() ?: throw CalcError("max needs arguments")
                "sum" -> a.sum()
                "avg", "mean" -> if (a.isEmpty()) throw CalcError("mean needs arguments") else a.average()
                "median" -> {
                    if (a.isEmpty()) throw CalcError("median needs arguments")
                    val s = a.sorted()
                    if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
                }
                "pow" -> a[0].pow(a[1])
                "hypot" -> kotlin.math.hypot(a[0], a[1])
                "fact" -> {
                    val n = one()
                    if (n < 0 || n != floor(n) || n > 170) throw CalcError("fact needs 0..170 integer")
                    var r = 1.0
                    for (i in 2..n.toInt()) r *= i
                    r
                }
                "gcd" -> {
                    var x = abs(a[0]).toLong(); var y = abs(a[1]).toLong()
                    while (y != 0L) { val t = y; y = x % y; x = t }
                    x.toDouble()
                }
                "rad" -> Math.toRadians(one())
                "deg" -> Math.toDegrees(one())
                else -> throw CalcError("Unknown function '$name'")
            }
        }
    }
}

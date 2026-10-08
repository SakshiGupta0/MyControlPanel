package app.mycontrolpanel.core

/**
 * Minimal JSON reader/writer (no dependencies) so persistence is pure Kotlin and testable on a plain JVM.
 * Values: Map<String, Any?>, List<Any?>, String, Double, Long, Boolean, null.
 */
object Json {
    fun write(v: Any?): String = StringBuilder().also { writeTo(it, v) }.toString()

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Float -> sb.append(v.toDouble().toString())
            is Double -> sb.append(if (v.isFinite()) v.toString() else "0")
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(','); first = false
                    writeString(sb, k.toString()); sb.append(':'); writeTo(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('['); var first = true
                for (item in v) { if (!first) sb.append(','); first = false; writeTo(sb, item) }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u%04x".format(c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    class ParseException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseException("Trailing data at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun fail(msg: String): Nothing = throw ParseException("$msg at $i")
        fun value(): Any? {
            if (i >= s.length) fail("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("Unexpected '$c'")
            }
        }
        fun lit(word: String, v: Any?): Any? { if (!s.startsWith(word, i)) fail("Bad literal"); i += word.length; return v }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return if (t.any { it in ".eE" }) t.toDoubleOrNull() ?: fail("Bad number") else t.toLongOrNull() ?: fail("Bad number")
        }
        fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("Bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("Bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> fail("Bad escape '$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++; val out = ArrayList<Any?>(); ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out += value(); ws()
                if (i >= s.length) fail("Unterminated array")
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> fail("Expected , or ]") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++; val out = LinkedHashMap<String, Any?>(); ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') fail("Expected key")
                val k = str(); ws()
                if (i >= s.length || s[i++] != ':') fail("Expected :")
                ws(); out[k] = value(); ws()
                if (i >= s.length) fail("Unterminated object")
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> fail("Expected , or }") }
            }
        }
    }
}

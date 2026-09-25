package com.xzo.agent.util

/**
 * Dependency-free syntax highlighting.
 *
 * Produces *spans*, not colours: the UI maps each token kind onto the neutral
 * gray ramp, so highlighting stays inside the monochrome design language instead
 * of splashing rainbow colours over a deliberately calm interface.
 */
object Highlighter {

    enum class Kind { PLAIN, KEYWORD, TYPE, STRING, NUMBER, COMMENT, PUNCT, FUNCTION, ANNOTATION }

    data class Span(val start: Int, val end: Int, val kind: Kind)

    private val commonKeywords = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "in", "is", "as", "new",
        "class", "interface", "object", "enum", "struct", "fun", "def", "func", "function",
        "val", "var", "let", "const", "static", "public", "private", "protected", "internal",
        "import", "from", "package", "namespace", "using", "include", "require",
        "try", "catch", "except", "finally", "throw", "throws", "raise", "with",
        "async", "await", "suspend", "yield", "lambda", "when", "switch", "case", "default",
        "true", "false", "null", "nil", "none", "undefined", "self", "this", "super",
        "and", "or", "not", "print", "echo", "select", "where", "insert", "update", "delete",
        "override", "abstract", "sealed", "data", "companion", "init", "typealias", "extends",
        "implements", "elif", "do", "end", "then", "begin", "module", "export", "type"
    )

    private val typeWords = setOf(
        "int", "long", "float", "double", "bool", "boolean", "string", "str", "char", "byte",
        "void", "any", "unit", "list", "map", "set", "dict", "array", "tuple", "object",
        "String", "Int", "Long", "Double", "Float", "Boolean", "List", "Map", "Set", "Array",
        "Unit", "Any", "Result", "Flow", "Optional", "Vec", "HashMap"
    )

    private val lineCommentPrefixes = listOf("//", "#", "--", ";")

    fun highlight(code: String, language: String = ""): List<Span> {
        val spans = mutableListOf<Span>()
        var i = 0
        val n = code.length
        val lang = language.lowercase()
        val hashComments = lang.isEmpty() || lang in setOf("python", "py", "sh", "bash", "yaml", "yml", "ruby", "rb", "toml", "ini")

        while (i < n) {
            val c = code[i]

            // block comments
            if (c == '/' && i + 1 < n && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                spans += Span(i, end, Kind.COMMENT); i = end; continue
            }
            // triple-quoted strings / docstrings
            if ((c == '"' || c == '\'') && i + 2 < n && code[i + 1] == c && code[i + 2] == c) {
                val marker = code.substring(i, i + 3)
                val end = code.indexOf(marker, i + 3).let { if (it < 0) n else it + 3 }
                spans += Span(i, end, Kind.STRING); i = end; continue
            }
            // line comments
            val prefix = lineCommentPrefixes.firstOrNull { p ->
                code.startsWith(p, i) && (p != "#" || hashComments)
            }
            if (prefix != null) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                spans += Span(i, end, Kind.COMMENT); i = end; continue
            }
            // strings
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                while (j < n && code[j] != c) {
                    if (code[j] == '\\') j++
                    j++
                }
                val end = (j + 1).coerceAtMost(n)
                spans += Span(i, end, Kind.STRING); i = end; continue
            }
            // annotations / decorators
            if (c == '@') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                if (j > i + 1) { spans += Span(i, j, Kind.ANNOTATION); i = j; continue }
            }
            // numbers
            if (c.isDigit()) {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                spans += Span(i, j, Kind.NUMBER); i = j; continue
            }
            // identifiers / keywords / calls
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                var k = j
                while (k < n && code[k] == ' ') k++
                val kind = when {
                    word.lowercase() in commonKeywords -> Kind.KEYWORD
                    word in typeWords || word.lowercase() in typeWords -> Kind.TYPE
                    k < n && code[k] == '(' -> Kind.FUNCTION
                    word.first().isUpperCase() -> Kind.TYPE
                    else -> Kind.PLAIN
                }
                if (kind != Kind.PLAIN) spans += Span(i, j, kind)
                i = j; continue
            }
            // punctuation
            if (!c.isWhitespace() && !c.isLetterOrDigit()) {
                spans += Span(i, i + 1, Kind.PUNCT); i++; continue
            }
            i++
        }
        return spans
    }
}

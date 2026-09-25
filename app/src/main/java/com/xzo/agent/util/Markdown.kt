package com.xzo.agent.util

/** Minimal, allocation-light markdown block parser (no third-party dependency). */
object Markdown {

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Code(val language: String, val code: String) : Block
        data class Bullet(val items: List<String>, val ordered: Boolean) : Block
        data class Quote(val text: String) : Block
        data class Table(val header: List<String>, val rows: List<List<String>>) : Block
        data object Divider : Block
    }

    fun parse(src: String): List<Block> {
        val out = mutableListOf<Block>()
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
            para.setLength(0)
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // fenced code
            if (trimmed.startsWith("```")) {
                flushPara()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                i++ // closing fence
                out += Block.Code(lang, code.toString().trimEnd())
                continue
            }

            // horizontal rule
            if (trimmed.matches(Regex("^([-*_])\\1{2,}$"))) {
                flushPara(); out += Block.Divider; i++; continue
            }

            // heading
            val h = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (h != null) {
                flushPara()
                out += Block.Heading(h.groupValues[1].length, h.groupValues[2].trim())
                i++; continue
            }

            // table
            if (trimmed.startsWith("|") && i + 1 < lines.size &&
                lines[i + 1].trim().matches(Regex("^\\|?[\\s:|-]+\\|?$")) &&
                lines[i + 1].contains("-")
            ) {
                flushPara()
                val header = splitRow(trimmed)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitRow(lines[i].trim()); i++
                }
                out += Block.Table(header, rows)
                continue
            }

            // quote
            if (trimmed.startsWith(">")) {
                flushPara()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    q.appendLine(lines[i].trim().removePrefix(">").trim()); i++
                }
                out += Block.Quote(q.toString().trim())
                continue
            }

            // lists
            val bulletRe = Regex("^[-*+]\\s+(.*)$")
            val orderedRe = Regex("^\\d+[.)]\\s+(.*)$")
            if (bulletRe.matches(trimmed) || orderedRe.matches(trimmed)) {
                flushPara()
                val ordered = orderedRe.matches(trimmed)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    val m = if (ordered) orderedRe.find(t) else bulletRe.find(t)
                    if (m == null) {
                        // continuation line of previous item
                        if (t.isNotBlank() && items.isNotEmpty() && lines[i].startsWith("  ")) {
                            items[items.lastIndex] = items.last() + " " + t
                            i++; continue
                        }
                        break
                    }
                    items += m.groupValues[1].trim()
                    i++
                }
                out += Block.Bullet(items, ordered)
                continue
            }

            if (trimmed.isEmpty()) {
                flushPara()
            } else {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
            i++
        }
        flushPara()
        return out
    }

    private fun splitRow(row: String): List<String> =
        row.trim().trim('|').split('|').map { it.trim() }

    /** Extract fenced code blocks (used by the "copy code" affordance). */
    fun codeBlocks(src: String): List<Pair<String, String>> =
        Regex("```(\\w*)\\n([\\s\\S]*?)```").findAll(src)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    fun stripMarkdown(src: String): String = src
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("[*_`#>]"), "")
        .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}

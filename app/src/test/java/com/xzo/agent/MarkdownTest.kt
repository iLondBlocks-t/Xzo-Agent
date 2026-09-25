package com.xzo.agent

import com.xzo.agent.util.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses headings lists and code`() {
        val blocks = Markdown.parse(
            """
            # Title
            Some text

            - one
            - two

            ```kotlin
            val x = 1
            ```
            """.trimIndent()
        )
        assertTrue(blocks.any { it is Markdown.Block.Heading && it.level == 1 })
        assertTrue(blocks.any { it is Markdown.Block.Bullet && it.items.size == 2 })
        val code = blocks.filterIsInstance<Markdown.Block.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `parses tables`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            |---|---|
            | 1 | 2 |
            """.trimIndent()
        )
        val table = blocks.filterIsInstance<Markdown.Block.Table>().single()
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun `strip markdown for speech`() {
        val plain = Markdown.stripMarkdown("## Hi **there** `code` [link](https://x.com)")
        assertTrue(plain.contains("Hi there"))
        assertTrue(!plain.contains("**"))
        assertTrue(!plain.contains("https://x.com"))
    }
}

class HighlighterTest {

    private fun kinds(code: String, lang: String = "kotlin") =
        com.xzo.agent.util.Highlighter.highlight(code, lang)
            .associate { code.substring(it.start, it.end) to it.kind }

    @Test
    fun `keywords strings numbers and comments are classified`() {
        val code = """
            // build the list
            val items = listOf("a", 42)
        """.trimIndent()
        val k = kinds(code)
        assertEquals(com.xzo.agent.util.Highlighter.Kind.KEYWORD, k["val"])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.FUNCTION, k["listOf"])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.STRING, k["\"a\""])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.NUMBER, k["42"])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.COMMENT, k["// build the list"])
    }

    @Test
    fun `python comments and decorators`() {
        val code = "@cache\ndef run(x):  # go\n    return x * 2"
        val k = kinds(code, "python")
        assertEquals(com.xzo.agent.util.Highlighter.Kind.ANNOTATION, k["@cache"])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.KEYWORD, k["def"])
        assertEquals(com.xzo.agent.util.Highlighter.Kind.COMMENT, k["# go"])
    }

    @Test
    fun `spans never overlap or exceed the input`() {
        val code = "fun main() { println(\"hi\") } // done"
        val spans = com.xzo.agent.util.Highlighter.highlight(code, "kotlin")
        var last = 0
        spans.forEach {
            assertTrue(it.start >= last)
            assertTrue(it.end <= code.length)
            assertTrue(it.end > it.start)
            last = it.end
        }
    }

    @Test
    fun `unterminated string does not hang or crash`() {
        val spans = com.xzo.agent.util.Highlighter.highlight("val x = \"oops", "kotlin")
        assertTrue(spans.isNotEmpty())
    }

    @Test
    fun `empty input is safe`() {
        assertTrue(com.xzo.agent.util.Highlighter.highlight("", "").isEmpty())
    }
}

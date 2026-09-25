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

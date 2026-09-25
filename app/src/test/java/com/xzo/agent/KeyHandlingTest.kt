package com.xzo.agent

import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.Provider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "API key rejected" class of failure is the number one reason a self-built
 * app looks broken, so the diagnosis path is covered by tests.
 */
class KeyHandlingTest {

    private fun client(primary: String = "", backup: String = "") = LlmClient(
        object : LlmClient.KeyProvider {
            override fun groqKey() = primary
            override fun openRouterKey() = backup
        }
    )

    @Test
    fun `missing key is reported as MISSING, never as rejected`() = runBlocking {
        val check = client().verifyKey(Provider.GROQ)
        assertEquals(LlmClient.KeyState.MISSING, check.state)
        assertFalse(check.ok)
        assertTrue(check.detail.contains("No key", ignoreCase = true))
    }

    @Test
    fun `half-pasted key is caught locally before any network call`() = runBlocking {
        val check = client(primary = "abc123").verifyKey(Provider.GROQ)
        assertEquals(LlmClient.KeyState.MALFORMED, check.state)
        assertTrue(check.detail.contains("characters"))
    }

    @Test
    fun `key containing whitespace is caught`() = runBlocking {
        val check = client(primary = "aaaaaaaaaa bbbbbbbbbbbbbbbbbbbb").verifyKey(Provider.GROQ)
        assertEquals(LlmClient.KeyState.MALFORMED, check.state)
    }

    @Test
    fun `key presence helpers are consistent`() {
        val c = client(primary = "  ")
        assertFalse(c.hasKeyFor(Provider.GROQ))
        assertFalse(c.hasAnyKey())

        val d = client(backup = "a".repeat(40))
        assertTrue(d.hasKeyFor(Provider.OPENROUTER))
        assertTrue(d.hasAnyKey())
        assertFalse(d.hasKeyFor(Provider.GROQ))
    }

    @Test
    fun `keys are trimmed so a trailing newline never breaks auth`() {
        val c = client(primary = "  " + "k".repeat(40) + "\n")
        assertEquals("k".repeat(40), c.keyFor(Provider.GROQ))
    }

    @Test
    fun `no vendor name leaks into any user facing label`() {
        val banned = listOf("groq", "openrouter", "openai", "chatgpt", "gemma", "llama", "qwen", "arena")
        ModelCatalog.all.forEach { spec ->
            val text = (spec.label + " " + spec.description).lowercase()
            banned.forEach { word ->
                assertFalse("'${spec.label}' leaks \"$word\"", text.contains(word))
            }
        }
        Provider.entries.forEach { p ->
            banned.forEach { word ->
                assertFalse("provider label leaks $word", p.label.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `friendly names hide raw identifiers`() {
        val name = ModelCatalog.friendlyName("openai/gpt-oss-120b")
        assertTrue(name.startsWith("Xzo "))
        assertFalse(name.contains("/"))
    }

    @Test
    fun `every catalogue entry has an Xzo style label`() {
        ModelCatalog.all.forEach {
            assertTrue("${it.id} → ${it.label}", it.label.startsWith("Xzo"))
        }
    }
}

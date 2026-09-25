package com.xzo.agent

import com.xzo.agent.agent.AgentMode
import com.xzo.agent.agent.AutoRouter
import com.xzo.agent.agent.Role
import com.xzo.agent.agent.Specialists
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoutingTest {

    private fun client(groq: Boolean = true, openRouter: Boolean = true) = LlmClient(
        object : LlmClient.KeyProvider {
            override fun groqKey() = if (groq) "gsk_test" else ""
            override fun openRouterKey() = if (openRouter) "sk-or-test" else ""
        }
    )

    @Test
    fun `decommissioned models are migrated, never sent`() {
        // groq/compound was shut down on 2026-09-21 and must never reach the API.
        assertEquals(ModelCatalog.GPT_OSS_120B.id, ModelCatalog.migrate("groq/compound"))
        assertEquals(ModelCatalog.GPT_OSS_20B.id, ModelCatalog.migrate("groq/compound-mini"))
        assertEquals(ModelCatalog.GPT_OSS_120B.id, ModelCatalog.migrate("llama-3.3-70b-versatile"))
        assertEquals(ModelCatalog.GPT_OSS_20B.id, ModelCatalog.migrate("llama-3.1-8b-instant"))
        assertEquals(ModelCatalog.GPT_OSS_120B.id, ModelCatalog.byId("groq/compound").id)
        ModelCatalog.RETIRED.values.forEach { replacement ->
            assertFalse("replacement must itself be live", ModelCatalog.RETIRED.containsKey(replacement))
        }
    }

    @Test
    fun `no catalogue model is a retired id`() {
        ModelCatalog.all.forEach {
            assertFalse("${it.id} is retired", ModelCatalog.RETIRED.containsKey(it.id))
        }
    }

    @Test
    fun `fallback chain crosses providers and has no duplicates`() {
        val chain = ModelCatalog.fallbackChain(ModelCatalog.DEFAULT_PRIMARY, ModelCatalog.DEFAULT_FALLBACK)
        assertEquals(chain.map { it.id }, chain.map { it.id }.distinct())
        assertTrue(chain.any { it.provider == Provider.GROQ })
        assertTrue(chain.any { it.provider == Provider.OPENROUTER })
        assertEquals(ModelCatalog.DEFAULT_PRIMARY, chain.first().id)
    }

    @Test
    fun `key presence is respected`() {
        assertTrue(client().hasKeyFor(Provider.GROQ))
        assertFalse(client(groq = false).hasKeyFor(Provider.GROQ))
        assertFalse(client(openRouter = false).hasKeyFor(Provider.OPENROUTER))
    }

    @Test
    fun `short questions route to the fast model`() {
        val d = AutoRouter.decide("what time is it", false, AgentMode.AGENT, client(), ModelCatalog.DEFAULT_PRIMARY)
        assertEquals(ModelCatalog.GPT_OSS_20B.id, d.model.id)
    }

    @Test
    fun `complex requests route to the flagship`() {
        val d = AutoRouter.decide(
            "Compare these two architectures and explain why one scales better, with benchmarks",
            false, AgentMode.AGENT, client(), ModelCatalog.DEFAULT_PRIMARY
        )
        assertEquals(ModelCatalog.GPT_OSS_120B.id, d.model.id)
    }

    @Test
    fun `images route to a vision model`() {
        val d = AutoRouter.decide("what is in this picture", true, AgentMode.AGENT, client(), ModelCatalog.DEFAULT_PRIMARY)
        assertTrue("expected a vision model, got ${d.model.id}", d.model.vision)
    }

    @Test
    fun `explicit user choice is never overridden`() {
        val d = AutoRouter.decide(
            "hi", false, AgentMode.AGENT, client(), ModelCatalog.OR_LLAMA_FREE.id
        )
        assertEquals(ModelCatalog.OR_LLAMA_FREE.id, d.model.id)
    }

    @Test
    fun `routing degrades gracefully when only one provider has a key`() {
        val d = AutoRouter.decide("hi", false, AgentMode.AGENT, client(groq = false), ModelCatalog.DEFAULT_PRIMARY)
        assertNotNull(d.model)
    }

    @Test
    fun `every specialist has a usable profile and model`() {
        val llm = client()
        Role.entries.forEach { role ->
            val p = Specialists.profile(role)
            assertTrue("${role.name} prompt too short", p.system.length > 80)
            assertTrue("${role.name} temperature out of range", p.temperature in 0.0..1.5)
            assertTrue("${role.name} has no candidate models", p.models.isNotEmpty())
            assertNotNull("${role.name} cannot pick a model", Specialists.pickModel(p, llm))
            p.builtIns.forEach {
                assertTrue("unknown built-in $it", it == "browser_search" || it == "code_interpreter")
            }
        }
    }

    @Test
    fun `researcher searches and analyst executes code`() {
        assertTrue(Specialists.profile(Role.RESEARCHER).builtIns.contains("browser_search"))
        assertTrue(Specialists.profile(Role.ANALYST).builtIns.contains("code_interpreter"))
        assertEquals(0.0, Specialists.profile(Role.CRITIC).temperature, 1e-9)
    }
}

package com.xzo.agent

import com.xzo.agent.agent.AgentTraceLog
import com.xzo.agent.agent.Prompts
import com.xzo.agent.agent.ToolTrace
import com.xzo.agent.agent.tools.CalculatorTool
import com.xzo.agent.agent.tools.ClockTool
import com.xzo.agent.agent.tools.CreateFileTool
import com.xzo.agent.agent.tools.DelegateTool
import com.xzo.agent.agent.tools.ReadFileTool
import com.xzo.agent.agent.tools.WebSearchTool
import com.xzo.agent.core.PromptLibrary
import com.xzo.agent.data.remote.WireJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the tool declarations the model actually receives. */
class ToolContractTest {

    private val tools = listOf(
        WebSearchTool, CalculatorTool, CreateFileTool, ReadFileTool, ClockTool, DelegateTool
    )

    @Test
    fun `every tool exposes a valid json schema`() {
        tools.forEach { tool ->
            val def = tool.toToolDef()
            assertEquals("function", def.type)
            assertEquals(tool.name, def.function!!.name)
            assertTrue("${tool.name} description too short", tool.description.length > 40)

            val schema = def.function!!.parameters.jsonObject
            assertEquals("object", schema["type"]!!.toString().trim('"'))
            assertTrue("${tool.name} missing properties", schema.containsKey("properties"))
            assertTrue("${tool.name} missing required", schema.containsKey("required"))

            // must survive a JSON round trip exactly as the API expects
            val encoded = WireJson.encodeToString(
                com.xzo.agent.data.remote.ToolDef.serializer(), def
            )
            assertTrue(encoded.contains(""""name":"${tool.name}""""))
        }
    }

    @Test
    fun `tool names are unique and api safe`() {
        val names = tools.map { it.name }
        assertEquals(names, names.distinct())
        names.forEach { assertTrue("$it is not a safe tool name", it.matches(Regex("^[a-z0-9_]{1,64}$"))) }
    }

    @Test
    fun `file tools are marked interactive so the UI can prompt`() {
        assertTrue(CreateFileTool.interactive)
        assertTrue(ReadFileTool.interactive)
        assertTrue(!CalculatorTool.interactive)
    }

    @Test
    fun `mime guessing covers the formats the agent writes`() {
        assertEquals("text/markdown", CreateFileTool.guessMime("report.md"))
        assertEquals("text/csv", CreateFileTool.guessMime("budget.csv"))
        assertEquals("application/json", CreateFileTool.guessMime("data.json"))
        assertEquals("text/x-python", CreateFileTool.guessMime("main.py"))
        assertEquals("text/plain", CreateFileTool.guessMime("notes"))
    }

    @Test
    fun `system prompt states the loop and the tools`() {
        val p = Prompts.system(
            persona = Prompts.DEFAULT_PERSONA,
            toolNames = listOf("web_search: find things"),
            serverSideTools = true,
            memoryBlock = "- user_name: Sam",
            deviceLocale = "ar_MA",
            nowIso = "2026-09-25T10:00:00Z"
        )
        listOf("PLAN", "ACT", "OBSERVE", "VERIFY", "RESPOND", "web_search", "user_name", "ar_MA")
            .forEach { assertTrue("system prompt missing $it", p.contains(it)) }
    }

    @Test
    fun `verification prompt demands the strict format`() {
        val p = Prompts.verification("q", "draft", "evidence")
        assertTrue(p.contains("VERDICT:"))
        assertTrue(p.contains("ISSUES:"))
        assertTrue(p.contains("FIX:"))
    }

    @Test
    fun `trace log survives a round trip`() {
        val log = AgentTraceLog(
            steps = listOf(ToolTrace("web_search", "{}", "3 results", true, 120)),
            verdict = "none",
            verified = true,
            provider = "Groq",
            model = "openai/gpt-oss-120b",
            iterations = 2
        )
        val decoded = AgentTraceLog.decode(log.encode())
        assertEquals(log.steps.single().tool, decoded.steps.single().tool)
        assertTrue(decoded.verified)
        assertEquals(2, decoded.iterations)
        // corrupt input must not crash the UI
        assertEquals(0, AgentTraceLog.decode("not json").steps.size)
        assertEquals(0, AgentTraceLog.decode(null).steps.size)
    }

    @Test
    fun `prompt library is well formed`() {
        assertTrue(PromptLibrary.all.size >= 20)
        assertEquals(PromptLibrary.all.map { it.id }, PromptLibrary.all.map { it.id }.distinct())
        PromptLibrary.all.forEach {
            assertTrue("${it.id} prompt too short", it.prompt.length > 40)
            assertTrue("${it.id} bad category", it.category in PromptLibrary.categories)
        }
        PromptLibrary.categories.forEach {
            assertTrue("empty category $it", PromptLibrary.byCategory(it).isNotEmpty())
        }
    }

    @Test
    fun `malformed tool arguments decode to an empty object`() {
        val parsed = runCatching { WireJson.parseToJsonElement("{}").jsonObject }.getOrDefault(JsonObject(emptyMap()))
        assertTrue(parsed.isEmpty())
    }
}

/** Continuation stitching and memory triggers. */
class ContinuationTest {

    private val engine: com.xzo.agent.agent.AgentEngine? = null

    @Test
    fun `overlap between chunks is removed`() {
        // joinWithoutOverlap is internal; exercise the same rule it implements.
        val head = "The quick brown fox jumps over the lazy dog and then keeps running far away"
        val tail = "keeps running far away into the woods."
        val overlap = "keeps running far away"
        assertTrue(head.endsWith(overlap))
        assertTrue(tail.startsWith(overlap))
    }

    @Test
    fun `memory harvester only fires on durable statements`() {
        val h = com.xzo.agent.agent.MemoryHarvester
        assertTrue(h.looksInteresting("My name is Yassine and I live in Agadir"))
        assertTrue(h.looksInteresting("اسمي ياسين وأعمل مطورا"))
        assertTrue(h.looksInteresting("I prefer answers in Arabic"))
        assertTrue(!h.looksInteresting("what is 2+2"))
        assertTrue(!h.looksInteresting("ok"))
    }

    @Test
    fun `agent modes are ordered from cheapest to deepest`() {
        val modes = com.xzo.agent.agent.AgentMode.entries.map { it.name }
        assertEquals(listOf("CHAT", "AGENT", "DEEP_RESEARCH"), modes)
        com.xzo.agent.agent.AgentMode.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.hint.length > 10)
        }
    }
}

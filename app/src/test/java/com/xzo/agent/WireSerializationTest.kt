package com.xzo.agent

import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.ChatResponse
import com.xzo.agent.data.remote.FunctionDef
import com.xzo.agent.data.remote.StreamChunk
import com.xzo.agent.data.remote.ToolDef
import com.xzo.agent.data.remote.WireJson
import com.xzo.agent.data.remote.WireMessage
import com.xzo.agent.data.remote.multimodal
import com.xzo.agent.data.remote.text
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down the exact JSON the app puts on the wire against both providers. */
class WireSerializationTest {

    @Test
    fun `plain text message serialises content as a string`() {
        val json = WireJson.encodeToString(WireMessage.serializer(), WireMessage("user", "hello"))
        assertEquals("""{"role":"user","content":"hello"}""", json)
    }

    @Test
    fun `built-in tool serialises without a function object`() {
        val json = WireJson.encodeToString(ToolDef.serializer(), ToolDef(type = "browser_search"))
        assertEquals("""{"type":"browser_search"}""", json)
    }

    @Test
    fun `function tool keeps its schema`() {
        val tool = ToolDef(
            function = FunctionDef(
                name = "create_file",
                description = "save a file",
                parameters = buildJsonObject { put("type", "object") }
            )
        )
        val json = WireJson.encodeToString(ToolDef.serializer(), tool)
        assertTrue(json.contains(""""type":"function""""))
        assertTrue(json.contains(""""name":"create_file""""))
    }

    @Test
    fun `multimodal message produces OpenAI style parts`() {
        val m = multimodal("user", "what is this?", listOf("data:image/jpeg;base64,AAA"))
        val json = WireJson.encodeToString(WireMessage.serializer(), m)
        assertTrue(json.contains(""""type":"text""""))
        assertTrue(json.contains(""""type":"image_url""""))
        assertTrue(json.contains("data:image/jpeg;base64,AAA"))
        assertEquals("what is this?", m.text)
    }

    @Test
    fun `groq request uses max_completion_tokens and reasoning effort`() {
        val req = ChatRequest(
            model = "openai/gpt-oss-120b",
            messages = listOf(WireMessage("user", "hi")),
            maxCompletionTokens = 512,
            reasoningEffort = "low"
        )
        val json = WireJson.encodeToString(ChatRequest.serializer(), req)
        assertTrue(json.contains(""""max_completion_tokens":512"""))
        assertTrue(json.contains(""""reasoning_effort":"low""""))
        assertTrue(!json.contains(""""max_tokens""""))
    }

    @Test
    fun `parses a tool-calling response`() {
        val body = """
        {"id":"x","model":"openai/gpt-oss-120b","choices":[{"index":0,"message":
        {"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function",
        "function":{"name":"web_search","arguments":"{\"query\":\"kotlin\"}"}}]},
        "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
        """.trimIndent()
        val parsed = WireJson.decodeFromString(ChatResponse.serializer(), body)
        val call = parsed.choices.single().message!!.toolCalls!!.single()
        assertEquals("web_search", call.function.name)
        assertEquals(15, parsed.usage!!.totalTokens)
    }

    @Test
    fun `parses groq executed_tools with search results`() {
        val body = """
        {"choices":[{"message":{"role":"assistant","content":"ok","executed_tools":
        [{"index":0,"type":"function","name":"browser_search","arguments":"q",
        "search_results":{"results":[{"title":"T","url":"https://a.b","content":"c","score":0.9}]},
        "code_results":[{"text":"42"}]}]}}]}
        """.trimIndent()
        val parsed = WireJson.decodeFromString(ChatResponse.serializer(), body)
        val executed = parsed.choices.single().message!!.executedTools!!.single()
        assertEquals("browser_search", executed.name)
        assertEquals("https://a.b", executed.searchResults!!.results!!.single().url)
        assertEquals("42", executed.codeResults!!.single().text)
    }

    @Test
    fun `parses streaming delta chunks including tool call fragments`() {
        val chunk = WireJson.decodeFromString(
            StreamChunk.serializer(),
            """{"choices":[{"index":0,"delta":{"content":"Hel","tool_calls":[{"index":0,"id":"a",
               "function":{"name":"calc","arguments":"{\"x\":"}}]}}]}"""
        )
        val delta = chunk.choices.single().delta!!
        assertEquals("Hel", delta.content)
        assertEquals("calc", delta.toolCalls!!.single().function!!.name)
    }

    @Test
    fun `unknown fields never break parsing`() {
        val parsed = WireJson.decodeFromString(
            ChatResponse.serializer(),
            """{"id":"1","brand_new_field":{"a":1},"choices":[{"message":{"role":"assistant","content":"hi"}}]}"""
        )
        assertEquals("hi", parsed.choices.single().message!!.text)
    }

    @Test
    fun `error payloads are readable`() {
        val parsed = WireJson.decodeFromString(
            ChatResponse.serializer(),
            """{"error":{"message":"The model `groq/compound` has been decommissioned",
               "type":"invalid_request_error","code":"model_decommissioned"}}"""
        )
        assertTrue(parsed.error!!.message.contains("decommissioned"))
        assertEquals(JsonPrimitive("model_decommissioned"), parsed.error!!.code)
    }
}

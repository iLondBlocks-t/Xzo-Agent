package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.intOr
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Real-time web search.
 *
 * When the selected model is `groq/compound`, Groq performs search server-side
 * and this tool is rarely called. For every other model it provides the same
 * capability through key-less endpoints (DuckDuckGo → Wikipedia).
 */
object WebSearchTool : AgentTool {
    override val name = "web_search"
    override val description =
        "Search the live web for current information, news, prices, documentation or facts. " +
            "Returns ranked results with title, URL and snippet. Use it whenever the answer " +
            "could depend on information after your training cutoff, or when the user asks " +
            "about anything current, local, or verifiable."

    override val parameters: JsonElement = schema(required = listOf("query")) {
        str("query", "The search query. Be specific; include key terms and, if relevant, the year.")
        int("max_results", "How many results to return (1-10).", 6)
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val q = args.str("query").trim()
        if (q.isEmpty()) return ToolResult.fail("query is required")
        val limit = args.intOr("max_results", 6).coerceIn(1, 10)
        ctx.emit("Searching the web for “$q”…")
        return try {
            val hits = ctx.web.search(q, limit)
            if (hits.isEmpty()) return ToolResult.ok(
                "No results found for \"$q\". Try rephrasing the query.",
                "No results for “$q”"
            )
            val body = buildString {
                appendLine("Web results for \"$q\":")
                hits.forEachIndexed { i, h ->
                    appendLine()
                    appendLine("[${i + 1}] ${h.title}")
                    if (h.url.isNotBlank()) appendLine("URL: ${h.url}")
                    if (h.snippet.isNotBlank()) appendLine(h.snippet.take(500))
                }
                appendLine()
                appendLine("Cite the URLs you actually used in your answer.")
            }
            ToolResult.ok(body, "${hits.size} results for “$q”")
        } catch (t: Throwable) {
            ToolResult.fail("Search failed: ${t.message}")
        }
    }
}

/** Fetch and read a specific page so the agent can go beyond snippets. */
object FetchUrlTool : AgentTool {
    override val name = "fetch_url"
    override val description =
        "Download a web page or plain-text/JSON endpoint and return its readable text content. " +
            "Use after web_search when you need details from a specific source, or when the user gives a URL."

    override val parameters: JsonElement = schema(required = listOf("url")) {
        str("url", "Absolute URL to fetch, e.g. https://example.com/article")
        int("max_chars", "Maximum characters of page text to return (500-20000).", 8000)
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val url = args.str("url").trim()
        if (url.isEmpty()) return ToolResult.fail("url is required")
        val max = args.intOr("max_chars", 8000).coerceIn(500, 20000)
        ctx.emit("Reading $url …")
        return try {
            val text = ctx.web.readPage(url, max)
            if (text.isBlank()) ToolResult.fail("The page returned no readable text.")
            else ToolResult.ok("Content of $url:\n\n$text", "Read ${text.length} chars from $url")
        } catch (t: Throwable) {
            ToolResult.fail("Could not fetch $url: ${t.message}")
        }
    }
}

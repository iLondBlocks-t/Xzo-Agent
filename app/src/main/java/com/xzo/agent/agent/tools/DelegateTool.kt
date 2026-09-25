package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.Role
import com.xzo.agent.agent.Specialists
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `delegate` — hand a sub-task to the specialist best suited to it.
 *
 * This is what turns Xzo from "one model with tools" into a small team: the
 * orchestrator stays in control of the conversation while heavy sub-tasks run on
 * the model tuned for that job (search-grounded researcher, sandboxed analyst,
 * code-executing coder, multilingual translator, adversarial critic…).
 */
object DelegateTool : AgentTool {
    override val name = "delegate"
    override val description =
        "Hand a self-contained sub-task to a specialist agent and get its result back. " +
            "Roles: RESEARCHER (live web facts + sources), ANALYST (data/maths, runs Python), " +
            "CODER (writes and executes code), WRITER (polished prose), TRANSLATOR (faithful " +
            "translation), CRITIC (finds errors in a draft), VISION (reads attached images), " +
            "PLANNER (breaks a big request into steps). Use it for anything heavy, specialised, " +
            "or that you want independently double-checked."

    override val parameters: JsonElement = schema(required = listOf("role", "task")) {
        str(
            "role", "Which specialist to use.",
            listOf("PLANNER", "RESEARCHER", "ANALYST", "CODER", "WRITER", "TRANSLATOR", "CRITIC", "VISION")
        )
        str("task", "A complete, self-contained instruction. The specialist cannot see the chat history.")
        str("context", "Any material the specialist needs: findings so far, data, the draft to critique.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val role = Role.from(args.str("role"))
        val task = args.str("task").trim()
        if (task.isEmpty()) return ToolResult.fail("task is required")
        val context = args.str("context")

        ctx.emit("${role.emoji} ${role.label} working…")

        return runCatching {
            val (result, spec) = Specialists.run(
                role = role,
                task = task,
                context = context,
                llm = ctx.llm,
                extraMessages = emptyList()
            )
            val sources = result.executedTools
                .flatMap { it.searchResults?.results.orEmpty() }
                .distinctBy { it.url }
                .take(8)
            val body = buildString {
                appendLine("### ${role.emoji} ${role.label} (${spec.label})")
                appendLine()
                appendLine(result.content.trim())
                if (sources.isNotEmpty()) {
                    appendLine()
                    appendLine("Sources found by the specialist:")
                    sources.forEach { appendLine("- ${it.title}: ${it.url}") }
                }
            }
            ToolResult.ok(body, "${role.emoji} ${role.label} → ${result.content.length} chars")
        }.getOrElse { ToolResult.fail("${role.label} failed: ${it.message}") }
    }
}

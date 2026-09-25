package com.xzo.agent.agent.tools

import com.xzo.agent.agent.AgentTool
import com.xzo.agent.agent.ToolContext
import com.xzo.agent.agent.ToolResult
import com.xzo.agent.agent.schema
import com.xzo.agent.agent.str
import com.xzo.agent.data.remote.ChatRequest
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.data.remote.ToolDef
import com.xzo.agent.data.remote.Provider
import com.xzo.agent.data.remote.WireMessage
import com.xzo.agent.util.Calc
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `code_execution`
 *
 * Two execution paths:
 *  1. **Local** – pure math/statistics expressions are evaluated on-device by
 *     [Calc], instantly and offline.
 *  2. **Remote** – anything else is delegated to the cloud sandbox, whose
 *     server-side Python sandbox actually runs the code and returns the output.
 */
object CodeExecutionTool : AgentTool {
    override val name = "code_execution"
    override val description =
        "Execute Python code or evaluate a mathematical expression and return the real output. " +
            "Use for any non-trivial arithmetic, unit conversion, statistics, date math, parsing " +
            "or data processing instead of computing it in your head."

    override val parameters: JsonElement = schema(required = listOf("code")) {
        str("code", "Python source to run, or a pure math expression such as '(2^31-1)/3600'.")
        str("purpose", "One short sentence describing what the code should produce.")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val code = args.str("code").trim()
        if (code.isEmpty()) return ToolResult.fail("code is required")
        ctx.emit("Running code…")

        // 1. Fast local path for plain expressions.
        val singleLine = code.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (singleLine.size == 1 && MATH_ONLY.matches(singleLine[0])) {
            runCatching { Calc.evalToString(singleLine[0]) }.onSuccess {
                return ToolResult.ok(
                    "Evaluated locally.\n${singleLine[0]} = $it",
                    "= $it"
                )
            }
        }

        // 2. Remote sandbox via the cloud engine.
        if (!ctx.llm.hasKeyFor(Provider.GROQ)) {
            return ToolResult.fail(
                "The cloud sandbox is not connected, so only plain math expressions can be computed offline. " +
                    "Add an access key in Settings to unlock full code execution."
            )
        }
        return try {
            val spec = ModelCatalog.GPT_OSS_20B
            val res = ctx.llm.complete(
                spec,
                ChatRequest(
                    model = spec.id,
                    messages = listOf(
                        WireMessage(
                            role = "system",
                            content = "You are a code runner. Execute the user's code with your Python tool. " +
                                "Reply with ONLY the resulting output. No commentary. " +
                                "If the code errors, return the traceback."
                        ),
                        WireMessage(role = "user", content = "```python\n$code\n```")
                    ),
                    temperature = 0.0,
                    maxCompletionTokens = 1600,
                    reasoningEffort = "low",
                    tools = listOf(ToolDef(type = "code_interpreter")),
                    toolChoice = "required"
                )
            )
            val executed = res.executedTools.firstOrNull()?.codeResults?.joinToString("\n") { it.text }
            val out = (executed?.takeIf { it.isNotBlank() } ?: res.content).trim()
            if (out.isEmpty()) ToolResult.fail("Sandbox returned no output.")
            else ToolResult.ok("Execution output:\n$out", out.lines().firstOrNull()?.take(90).orEmpty())
        } catch (t: Throwable) {
            // Last resort: still try local evaluation of the last expression line.
            val fallback = singleLine.lastOrNull()?.let { l -> runCatching { Calc.evalToString(l) }.getOrNull() }
            if (fallback != null) ToolResult.ok("Sandbox unavailable; evaluated locally: $fallback", "= $fallback")
            else ToolResult.fail("Code execution failed: ${t.message}")
        }
    }

    private val MATH_ONLY = Regex("^[-+*/%^().,0-9\\s a-z_]{1,300}$")
}

/** Deterministic offline calculator, always available even with no network. */
object CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description =
        "Evaluate a math expression fully offline and exactly. Supports + - * / % ^, parentheses, " +
            "pi/e, sqrt, ln, log, sin/cos/tan, min/max/sum/mean/median, fact, gcd, rad/deg."

    override val parameters: JsonElement = schema(required = listOf("expression")) {
        str("expression", "e.g. '17.5% of 240' should be written as '0.175*240'")
    }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val expr = args.str("expression").trim()
        if (expr.isEmpty()) return ToolResult.fail("expression is required")
        return runCatching { Calc.evalToString(expr) }
            .fold(
                onSuccess = { ToolResult.ok("$expr = $it", "$expr = $it") },
                onFailure = { ToolResult.fail("Cannot evaluate '$expr': ${it.message}") }
            )
    }
}

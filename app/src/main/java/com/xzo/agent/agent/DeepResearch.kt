package com.xzo.agent.agent

import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.WireJson
import com.xzo.agent.data.remote.WireMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Deep Research mode.
 *
 * A deterministic, Kotlin-driven pipeline instead of hoping one model does it all:
 *
 *   1. **Plan**      – Planner decomposes the question into 3-5 independent sub-questions.
 *   2. **Investigate** – every sub-question runs **in parallel** on a search-grounded
 *                      Researcher (or Analyst/Coder when the planner asks for one).
 *   3. **Synthesise** – Writer merges the findings into one grounded answer with sources.
 *   4. **Critique**   – Critic attacks the draft.
 *   5. **Revise**     – Writer applies the critique; the result is what the user sees.
 *
 * Running step 2 concurrently is what makes this fast enough to be usable on a phone:
 * five searches happen in the time of the slowest one, not the sum of all five.
 */
class DeepResearch(private val llm: LlmClient) {

    data class SubTask(val role: Role, val task: String, val why: String = "")

    data class Finding(
        val subTask: SubTask,
        val content: String,
        val model: String,
        val sources: List<String>,
        val ok: Boolean,
        val durationMs: Long
    )

    data class Report(
        val answer: String,
        val plan: List<SubTask>,
        val findings: List<Finding>,
        val critique: String,
        val revised: Boolean
    )

    suspend fun run(
        question: String,
        conversationContext: String,
        maxSubTasks: Int,
        onEvent: suspend (AgentEvent) -> Unit
    ): Report = coroutineScope {

        /* ---------------- 1. plan ---------------- */
        onEvent(AgentEvent.Status("🧭 Planning the investigation…"))
        val plan = runCatching { plan(question, conversationContext, maxSubTasks) }
            .getOrElse { emptyList() }
            .ifEmpty {
                listOf(
                    SubTask(Role.RESEARCHER, "Find the core, current facts needed to answer: $question"),
                    SubTask(Role.RESEARCHER, "Find recent developments, numbers and expert opinion about: $question"),
                    SubTask(Role.RESEARCHER, "Find counter-arguments, risks or contradicting sources about: $question")
                )
            }
            .take(maxSubTasks)

        onEvent(AgentEvent.Plan(plan.joinToString("\n") { "${it.role.emoji} ${it.task}" }))

        /* ---------------- 2. investigate (parallel) ---------------- */
        onEvent(AgentEvent.Status("Investigating ${plan.size} threads in parallel…"))
        val findings = plan.map { sub ->
            async {
                val started = System.currentTimeMillis()
                onEvent(AgentEvent.ToolStart("${sub.role.emoji} ${sub.role.label}", sub.task.take(120)))
                val result = runCatching {
                    Specialists.run(sub.role, sub.task, conversationContext.take(4000), llm)
                }.getOrNull()
                val elapsed = System.currentTimeMillis() - started
                val sources = result?.first?.executedTools
                    ?.flatMap { it.searchResults?.results.orEmpty() }
                    ?.map { it.url }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                val finding = Finding(
                    subTask = sub,
                    content = result?.first?.content.orEmpty(),
                    model = result?.second?.label.orEmpty(),
                    sources = sources,
                    ok = !result?.first?.content.isNullOrBlank(),
                    durationMs = elapsed
                )
                onEvent(
                    AgentEvent.ToolEnd(
                        ToolTrace(
                            tool = "${sub.role.emoji} ${sub.role.label}",
                            argsPreview = sub.task.take(140),
                            summary = if (finding.ok)
                                "${finding.content.length} chars · ${sources.size} sources · ${finding.model}"
                            else "no result",
                            ok = finding.ok,
                            durationMs = elapsed,
                            fullOutput = finding.content.take(6000)
                        )
                    )
                )
                finding
            }
        }.awaitAll()

        val material = buildString {
            appendLine("# Research question")
            appendLine(question)
            appendLine()
            findings.filter { it.ok }.forEachIndexed { i, f ->
                appendLine("## Finding ${i + 1} — ${f.subTask.role.label}")
                appendLine("Sub-question: ${f.subTask.task}")
                appendLine()
                appendLine(f.content.take(9000))
                if (f.sources.isNotEmpty()) {
                    appendLine()
                    appendLine("URLs: " + f.sources.take(8).joinToString(", "))
                }
                appendLine()
            }
        }

        if (findings.none { it.ok }) {
            return@coroutineScope Report(
                answer = "⚠️ Every research thread failed — the providers are probably rate limited. " +
                    "Wait a few seconds and try again, or switch to normal Agent mode.",
                plan = plan, findings = findings, critique = "", revised = false
            )
        }

        /* ---------------- 3. synthesise ---------------- */
        onEvent(AgentEvent.Status("✍️ Writing the report…"))
        val draft = runCatching {
            Specialists.run(
                Role.WRITER,
                """
                Write the final answer to this question: "$question"

                Use ONLY the findings in the context. Structure it as:
                a direct answer first (2-4 sentences), then the detail with headings,
                then "## Sources" listing every URL you used.
                Note explicitly anything the research could not establish.
                Answer in the same language as the question.
                """.trimIndent(),
                material,
                llm,
                useBuiltIns = false
            ).first.content
        }.getOrElse { material }

        /* ---------------- 4. critique ---------------- */
        onEvent(AgentEvent.Status("🔬 Critic reviewing…"))
        val critique = runCatching {
            Specialists.run(
                Role.CRITIC,
                """
                Review this draft answer to "$question".
                List concrete problems as short bullets: unsupported claims, missing parts of the
                question, arithmetic errors, stale or missing sources. If it is sound, reply exactly: OK.
                """.trimIndent(),
                "DRAFT:\n$draft\n\nEVIDENCE:\n${material.take(12000)}",
                llm,
                useBuiltIns = false
            ).first.content.trim()
        }.getOrDefault("OK")

        /* ---------------- 5. revise ---------------- */
        val needsRevision = !critique.equals("OK", true) &&
            critique.length > 24 &&
            !critique.startsWith("OK", true)

        val finalAnswer = if (!needsRevision) draft else {
            onEvent(AgentEvent.Status("Applying the critique…"))
            runCatching {
                Specialists.run(
                    Role.WRITER,
                    "Rewrite the draft so every point in the critique is fixed. Keep the structure " +
                        "and every valid source. Output only the corrected final answer.",
                    "QUESTION:\n$question\n\nDRAFT:\n$draft\n\nCRITIQUE:\n$critique\n\n" +
                        "EVIDENCE:\n${material.take(10000)}",
                    llm,
                    useBuiltIns = false
                ).first.content
            }.getOrDefault(draft)
        }

        Report(
            answer = finalAnswer.trim(),
            plan = plan,
            findings = findings,
            critique = critique,
            revised = needsRevision
        )
    }

    private suspend fun plan(question: String, context: String, max: Int): List<SubTask> {
        val (result, _) = Specialists.run(
            Role.PLANNER,
            "Decompose this request into at most $max independent sub-tasks: $question",
            context.take(4000),
            llm,
            useBuiltIns = false,
            extraMessages = listOf(
                WireMessage(
                    "user",
                    "Reminder: reply with a bare JSON array only, no markdown fence."
                )
            )
        )
        val raw = result.content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = runCatching { WireJson.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val task = o["task"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (task.isEmpty()) return@mapNotNull null
            SubTask(
                role = Role.from(o["role"]?.jsonPrimitive?.content),
                task = task,
                why = o["why"]?.jsonPrimitive?.content.orEmpty()
            )
        }
    }
}

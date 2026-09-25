package com.xzo.agent.data.remote

import com.xzo.agent.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LlmException(
    message: String,
    val httpCode: Int = -1,
    val retryable: Boolean = false,
    val rateLimited: Boolean = false,
    val retryAfterMs: Long = 0L,
    /** No key is configured at all — the user has not finished setup. */
    val missingKey: Boolean = false,
    /** A key exists but the route refused it (mistyped, revoked, disabled). */
    val rejectedKey: Boolean = false
) : Exception(message)

/** Accumulated result of a single model turn. */
data class TurnResult(
    val content: String,
    val toolCalls: List<ToolCall>,
    val finishReason: String?,
    val model: String,
    val provider: Provider,
    val usage: Usage?,
    val reasoning: String? = null,
    val executedTools: List<ExecutedTool> = emptyList()
)

private val BLOCKED_PREFIXES = listOf(
    "whisper", "distil-whisper", "playai-tts", "canopylabs/", "meta-llama/llama-guard",
    "meta-llama/llama-prompt-guard"
)

class LlmClient(
    private val keyProvider: KeyProvider = KeyProvider.Default
) {

    interface KeyProvider {
        fun groqKey(): String
        fun openRouterKey(): String

        object Default : KeyProvider {
            override fun groqKey() = BuildConfig.GROQ_API_KEY
            override fun openRouterKey() = BuildConfig.OPENROUTER_API_KEY
        }
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun hasKeyFor(provider: Provider): Boolean = keyFor(provider).isNotBlank()

    fun keyFor(provider: Provider): String = when (provider) {
        Provider.GROQ -> keyProvider.groqKey().trim()
        Provider.OPENROUTER -> keyProvider.openRouterKey().trim()
    }

    fun hasAnyKey(): Boolean = Provider.entries.any { hasKeyFor(it) }

    /** Thrown instead of firing a request that is guaranteed to come back 401. */
    private fun requireKey(spec: ModelSpec) {
        if (keyFor(spec.provider).isBlank()) {
            throw LlmException(
                "No access key is set up yet, so Xzo cannot reach its cloud brain. " +
                    "Open Settings → Access keys and paste a key.",
                httpCode = 0,
                retryable = false,
                missingKey = true
            )
        }
    }

    /* ----------------------------- key checking ----------------------------- */

    enum class KeyState { OK, MISSING, MALFORMED, REJECTED, RATE_LIMITED, OFFLINE, UNKNOWN }

    data class KeyCheck(val state: KeyState, val detail: String) {
        val ok: Boolean get() = state == KeyState.OK
    }

    /**
     * Cheap live probe so the user learns *precisely* what is wrong instead of a
     * bare "rejected": not set, wrong shape, revoked, or simply out of capacity.
     */
    suspend fun verifyKey(provider: Provider): KeyCheck = withContext(Dispatchers.IO) {
        val key = keyFor(provider)
        if (key.isBlank()) {
            return@withContext KeyCheck(KeyState.MISSING, "No key saved for this route yet.")
        }
        if (key.length < 20 || key.any { it.isWhitespace() }) {
            return@withContext KeyCheck(
                KeyState.MALFORMED,
                "That does not look like a complete key — it is ${key.length} characters and keys are much longer. " +
                    "Copy the whole key, with no spaces or line breaks."
            )
        }
        val request = Request.Builder()
            .url(provider.modelsEndpoint)
            .get()
            .header("Authorization", "Bearer $key")
            .build()
        try {
            http.newCall(request).executeSuspending().use { r ->
                RateLimitTracker.record(provider, r)
                val body = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> {
                        val count = runCatching {
                            WireJson.decodeFromString(ModelListResponse.serializer(), body).data.size
                        }.getOrDefault(0)
                        KeyCheck(KeyState.OK, "Connected · $count brains reachable")
                    }
                    r.code == 401 || r.code == 403 -> KeyCheck(
                        KeyState.REJECTED,
                        "The key was refused. It is either mistyped, revoked, or was disabled after being " +
                            "made public. Generate a fresh key and paste it again."
                    )
                    r.code == 429 -> KeyCheck(
                        KeyState.RATE_LIMITED,
                        "The key works, but the route is at capacity right now. Try again shortly."
                    )
                    else -> KeyCheck(KeyState.UNKNOWN, "Unexpected response ${r.code}. ${friendlyMessage(body).take(120)}")
                }
            }
        } catch (t: Throwable) {
            KeyCheck(KeyState.OFFLINE, "Could not reach the network: ${t.message ?: "no connection"}")
        }
    }

    /**
     * Normalises a request for a specific provider/model:
     *  - the primary route wants `max_completion_tokens`; the backup route wants `max_tokens`.
     *  - server-side tools are appended for models that support them.
     *  - Models without client tool-calling get the tools array stripped.
     */
    fun adapt(spec: ModelSpec, request: ChatRequest, allowBuiltIns: Boolean): ChatRequest {
        val clientTools = if (spec.clientTools) request.tools.orEmpty() else emptyList()
        val builtIns = if (allowBuiltIns && spec.provider == Provider.GROQ)
            spec.builtInTools.map { ToolDef(type = it, function = null) } else emptyList()
        val tools = (clientTools + builtIns).ifEmpty { null }
        val limit = request.maxTokens ?: request.maxCompletionTokens
        return request.copy(
            model = spec.id,
            tools = tools,
            toolChoice = if (tools == null) null else request.toolChoice,
            maxTokens = if (spec.provider == Provider.OPENROUTER) limit else null,
            maxCompletionTokens = if (spec.provider == Provider.GROQ) limit else null,
            reasoningEffort = if (spec.reasoning && spec.provider == Provider.GROQ)
                (request.reasoningEffort ?: "medium") else null
        )
    }

    private fun buildRequest(spec: ModelSpec, bodyJson: String): Request {
        requireKey(spec)
        val b = Request.Builder()
            .url(spec.provider.endpoint)
            .post(bodyJson.toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
        when (spec.provider) {
            Provider.GROQ -> b.header("Authorization", "Bearer ${keyProvider.groqKey()}")
            Provider.OPENROUTER -> {
                b.header("Authorization", "Bearer ${keyProvider.openRouterKey()}")
                b.header("HTTP-Referer", "https://github.com/iLondBlocks-t/Xzo-Agent")
                b.header("X-Title", "Xzo Agent")
            }
        }
        return b.build()
    }

    /** Non-streaming single call. Throws [LlmException] on failure. */
    suspend fun complete(spec: ModelSpec, request: ChatRequest): TurnResult =
        withContext(Dispatchers.IO) {
            val body = WireJson.encodeToString(ChatRequest.serializer(), request.copy(stream = false))
            val call = http.newCall(buildRequest(spec, body))
            val response = call.executeSuspending()
            response.use { r ->
                RateLimitTracker.record(spec.provider, r)
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw errorFor(r, text)
                val parsed = runCatching { WireJson.decodeFromString(ChatResponse.serializer(), text) }
                    .getOrElse { throw LlmException("Malformed response from ${spec.provider.label}: ${it.message}") }
                parsed.error?.let { throw LlmException(it.message.ifBlank { "Provider error" }) }
                val choice = parsed.choices.firstOrNull()
                    ?: throw LlmException("Empty response from ${spec.provider.label}")
                val msg = choice.message ?: WireMessage("assistant", "")
                TurnResult(
                    content = msg.text,
                    toolCalls = msg.toolCalls.orEmpty(),
                    finishReason = choice.finishReason,
                    model = parsed.model.ifBlank { spec.id },
                    provider = spec.provider,
                    usage = parsed.usage,
                    reasoning = msg.reasoning,
                    executedTools = msg.executedTools.orEmpty()
                )
            }
        }

    /**
     * Streaming call. [onDelta] receives incremental assistant text.
     * Tool-call deltas are accumulated and returned in the final [TurnResult].
     */
    suspend fun stream(
        spec: ModelSpec,
        request: ChatRequest,
        onDelta: suspend (String) -> Unit
    ): TurnResult = withContext(Dispatchers.IO) {
        val body = WireJson.encodeToString(ChatRequest.serializer(), request.copy(stream = true))
        val call = http.newCall(buildRequest(spec, body))
        val response = call.executeSuspending()
        response.use { r ->
            RateLimitTracker.record(spec.provider, r)
            if (!r.isSuccessful) {
                val errText = r.body?.string().orEmpty()
                throw errorFor(r, errText)
            }
            val source = r.body?.source() ?: throw LlmException("No response body")
            val sb = StringBuilder()
            val partials = LinkedHashMap<Int, MutablePartialCall>()
            var finish: String? = null
            var usage: Usage? = null
            var modelName = spec.id

            while (true) {
                coroutineContext.ensureActive()
                val line = try {
                    source.readUtf8Line() ?: break
                } catch (ce: CancellationException) {
                    throw ce
                } catch (io: IOException) {
                    break
                }
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val chunk = runCatching { WireJson.decodeFromString(StreamChunk.serializer(), payload) }
                    .getOrNull() ?: continue
                if (chunk.model.isNotBlank()) modelName = chunk.model
                chunk.usage?.let { usage = it }
                val choice = chunk.choices.firstOrNull() ?: continue
                choice.finishReason?.let { finish = it }
                val delta = choice.delta ?: continue
                delta.content?.let { piece ->
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        onDelta(piece)
                    }
                }
                delta.toolCalls?.forEach { d ->
                    val p = partials.getOrPut(d.index) { MutablePartialCall() }
                    d.id?.let { p.id = it }
                    d.function?.name?.let { p.name = it }
                    d.function?.arguments?.let { p.args.append(it) }
                }
            }

            TurnResult(
                content = sb.toString(),
                toolCalls = partials.values.filter { it.name.isNotBlank() }.map {
                    ToolCall(
                        id = it.id.ifBlank { "call_${it.name}_${System.nanoTime()}" },
                        function = FunctionCall(it.name, it.args.toString().ifBlank { "{}" })
                    )
                },
                finishReason = finish,
                model = modelName,
                provider = spec.provider,
                usage = usage
            )
        }
    }

    private class MutablePartialCall {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }

    /**
     * Speech-to-text through the cloud speech engine (free tier). Used by the mic button.
     * Returns the transcript, or throws [LlmException].
     */
    suspend fun transcribe(file: java.io.File, language: String? = null): String =
        withContext(Dispatchers.IO) {
            if (!hasKeyFor(Provider.GROQ)) throw LlmException("Voice input needs an Xzo access key. Add one in Settings.")
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("audio/mp4".toMediaType())
                )
                .addFormDataPart("model", ModelCatalog.WHISPER_TURBO)
                .addFormDataPart("response_format", "json")
                .apply { if (!language.isNullOrBlank()) addFormDataPart("language", language) }
                .build()
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer ${'$'}{keyProvider.groqKey()}")
                .post(body)
                .build()
            http.newCall(req).executeSuspending().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw errorFor(r, text)
                runCatching {
                    WireJson.parseToJsonElement(text).let { el ->
                        (el as kotlinx.serialization.json.JsonObject)["text"]
                            ?.let { p -> (p as kotlinx.serialization.json.JsonPrimitive).content }
                    }
                }.getOrNull().orEmpty()
            }
        }

    /** Live model discovery from the provider's OpenAI-compatible /models endpoint. */
    suspend fun listModels(provider: Provider): List<ModelSpec> = withContext(Dispatchers.IO) {
        if (!hasKeyFor(provider)) return@withContext emptyList()
        val b = Request.Builder().url(provider.modelsEndpoint).get()
        when (provider) {
            Provider.GROQ -> b.header("Authorization", "Bearer ${'$'}{keyProvider.groqKey()}")
            Provider.OPENROUTER -> b.header("Authorization", "Bearer ${'$'}{keyProvider.openRouterKey()}")
        }
        val response = http.newCall(b.build()).executeSuspending()
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFor(r, text)
            val parsed = WireJson.decodeFromString(ModelListResponse.serializer(), text)
            parsed.data
                .filter { it.active && it.id.isNotBlank() }
                .filterNot { id -> BLOCKED_PREFIXES.any { id.id.startsWith(it) } }
                .map { e ->
                    val known = ModelCatalog.all.firstOrNull { it.id == e.id }
                    known ?: ModelSpec(
                        id = e.id,
                        provider = provider,
                        label = e.name ?: e.id,
                        description = e.description?.take(140)
                            ?: "Discovered from ${'$'}{provider.label} /models",
                        contextTokens = e.contextWindow ?: e.contextLength ?: 8192,
                        free = e.id.endsWith(":free") ||
                            (e.pricing?.prompt == "0" || e.pricing?.prompt == "0.0"),
                        clientTools = true
                    )
                }
                .sortedBy { it.id }
        }
    }

    private fun friendlyMessage(text: String): String = runCatching {
        WireJson.decodeFromString(ChatResponse.serializer(), text).error?.message.orEmpty()
    }.getOrDefault("")

    private fun errorFor(r: Response, text: String): LlmException {
        val friendly = extractMessage(text)
        val retryAfter = r.header("retry-after")?.toLongOrNull()?.times(1000) ?: 0L
        return when (r.code) {
            401, 403 -> LlmException(
                "Your access key was refused (${r.code}) — it is mistyped, revoked, or was disabled " +
                    "after being exposed publicly. Create a fresh key and paste it in Settings → Access keys. " +
                    friendly,
                r.code,
                rejectedKey = true
            )
            400 -> LlmException(
                if (friendly.contains("decommission", true) || friendly.contains("does not exist", true))
                    "That brain has been retired. Xzo is switching to a current one. $friendly".trim()
                else "Bad request (400). $friendly".trim(),
                r.code,
                retryable = friendly.contains("decommission", true) ||
                    friendly.contains("does not exist", true) ||
                    friendly.contains("tool", true)
            )
            404 -> LlmException("That brain is not reachable on this route (404). $friendly".trim(), r.code, retryable = true)
            408, 409, 425 -> LlmException("Transient error ${r.code}. $friendly".trim(), r.code, retryable = true)
            429 -> LlmException(
                "This route is at capacity right now. $friendly".trim(),
                r.code, retryable = true, rateLimited = true,
                retryAfterMs = if (retryAfter > 0) retryAfter else 4000L
            )
            in 500..599 -> LlmException("The route had a server error (${r.code}). $friendly".trim(), r.code, retryable = true)
            else -> LlmException("HTTP ${r.code}. $friendly".trim(), r.code)
        }
    }

    private fun extractMessage(text: String): String {
        if (text.isBlank()) return ""
        return runCatching {
            val resp = WireJson.decodeFromString(ChatResponse.serializer(), text)
            val code = (resp.error?.code as? JsonPrimitive)?.let { it.contentOrNull ?: it.intOrNull?.toString() }
            listOfNotNull(resp.error?.message?.takeIf { it.isNotBlank() }, code?.let { "($it)" })
                .joinToString(" ")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(240)
    }
}

/** Suspending bridge over OkHttp that truly cancels the HTTP call on coroutine cancellation. */
internal suspend fun Call.executeSuspending(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        LlmException("Network error: ${e.message ?: "unreachable"}", retryable = true)
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

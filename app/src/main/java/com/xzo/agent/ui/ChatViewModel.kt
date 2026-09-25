package com.xzo.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xzo.agent.AppContainer
import com.xzo.agent.XzoApp
import com.xzo.agent.agent.AgentEvent
import com.xzo.agent.agent.AgentInput
import com.xzo.agent.agent.AgentTraceLog
import com.xzo.agent.agent.Attachment
import com.xzo.agent.agent.ToolTrace
import com.xzo.agent.core.AppSettings
import com.xzo.agent.data.db.ArtifactEntity
import com.xzo.agent.data.db.ConversationEntity
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.data.remote.ModelCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: Long = 0,
    val title: String = "New chat",
    val messages: List<MessageEntity> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val artifacts: List<ArtifactEntity> = emptyList(),
    val input: String = "",
    val attachments: List<Attachment> = emptyList(),
    val busy: Boolean = false,
    val verifying: Boolean = false,
    val status: String? = null,
    val streamingText: String = "",
    val liveTraces: List<ToolTrace> = emptyList(),
    val banner: String? = null,
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val searchResults: List<MessageEntity> = emptyList()
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val container: AppContainer = (app as XzoApp).container
    private val repo = container.chatRepo

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val currentId = MutableStateFlow(0L)
    private var runJob: Job? = null

    val fileRequests = container.files.requests

    init {
        viewModelScope.launch {
            container.settingsRepo.flow.collectLatest { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            repo.conversations().collectLatest { list -> _state.update { it.copy(conversations = list) } }
        }
        viewModelScope.launch {
            repo.artifacts().collectLatest { list -> _state.update { it.copy(artifacts = list) } }
        }
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            currentId.flatMapLatest { id -> repo.messages(id) }
                .collectLatest { msgs -> _state.update { it.copy(messages = msgs) } }
        }
        viewModelScope.launch {
            @Suppress("OPT_IN_USAGE")
            currentId.flatMapLatest { id -> repo.conversation(id) }
                .collectLatest { c -> _state.update { it.copy(title = c?.title ?: "New chat") } }
        }
        viewModelScope.launch {
            val first = repo.conversations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
            val id = first.firstOrNull()?.id ?: repo.newConversation(container.settingsSnapshot.primaryModel)
            select(id)
        }
    }

    /* ------------------------------ navigation ------------------------------ */

    fun select(id: Long) {
        currentId.value = id
        _state.update { it.copy(conversationId = id, streamingText = "", liveTraces = emptyList(), status = null) }
    }

    fun newChat() = viewModelScope.launch {
        val id = repo.newConversation(_state.value.settings.primaryModel)
        select(id)
    }

    fun deleteChat(id: Long) = viewModelScope.launch {
        repo.deleteConversation(id)
        if (_state.value.conversationId == id) {
            val next = _state.value.conversations.firstOrNull { it.id != id }?.id
                ?: repo.newConversation(_state.value.settings.primaryModel)
            select(next)
        }
    }

    fun renameChat(id: Long, title: String) = viewModelScope.launch { repo.rename(id, title) }
    fun pinChat(id: Long, pinned: Boolean) = viewModelScope.launch { repo.setPinned(id, pinned) }
    fun clearCurrent() = viewModelScope.launch { repo.clearConversation(_state.value.conversationId) }

    fun exportCurrent() = viewModelScope.launch {
        val saved = repo.exportConversation(_state.value.conversationId)
        banner(if (saved != null) "Exported to ${saved.name}" else "Export cancelled")
    }

    fun search(q: String) = viewModelScope.launch {
        _state.update { it.copy(searchQuery = q) }
        val res = if (q.isBlank()) emptyList() else repo.searchMessages(q)
        _state.update { it.copy(searchResults = res) }
    }

    /* ------------------------------ composing ------------------------------ */

    fun onInputChange(v: String) = _state.update { it.copy(input = v) }

    fun banner(text: String?) = _state.update { it.copy(banner = text) }

    fun attachFile() = viewModelScope.launch {
        val loaded = container.files.openDocument(arrayOf("*/*"))
        if (loaded == null) {
            banner("No file selected")
        } else if (loaded.text.isBlank()) {
            banner("“${loaded.name}” has no readable text")
        } else {
            _state.update {
                it.copy(
                    attachments = it.attachments + Attachment(
                        loaded.name, loaded.mime, loaded.text, loaded.uri.toString()
                    )
                )
            }
            banner("Attached ${loaded.name} (${loaded.text.length} chars)")
        }
    }

    fun removeAttachment(uri: String) =
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.uri == uri }) }

    fun stop() {
        runJob?.cancel()
        container.files.cancelAll()
        _state.update { it.copy(busy = false, status = null, verifying = false) }
    }

    fun retryLast() {
        val last = _state.value.messages.lastOrNull { it.role == "user" } ?: return
        viewModelScope.launch {
            _state.value.messages.lastOrNull()?.takeIf { it.role == "assistant" }?.let { repo.deleteMessage(it.id) }
            send(last.content, reuseText = true)
        }
    }

    fun send(text: String = _state.value.input, reuseText: Boolean = false) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.busy) return
        val s = _state.value
        val attachments = s.attachments

        runJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    input = if (reuseText) it.input else "",
                    attachments = emptyList(),
                    busy = true,
                    status = "Planning…",
                    streamingText = "",
                    liveTraces = emptyList(),
                    verifying = false,
                    banner = null
                )
            }

            val cid = repo.ensureConversation(s.conversationId.takeIf { it > 0 }, s.settings.primaryModel)
            if (cid != s.conversationId) select(cid)

            val history = repo.wireHistory(cid, s.settings.historyWindow)

            repo.addMessage(
                MessageEntity(
                    conversationId = cid,
                    role = "user",
                    content = prompt,
                    attachmentsJson = if (attachments.isEmpty()) null
                    else attachments.joinToString(", ") { it.name }
                )
            )

            val started = System.currentTimeMillis()
            val input = AgentInput(
                userText = prompt,
                history = history,
                attachments = attachments,
                modelId = s.settings.primaryModel,
                fallbackModelId = s.settings.fallbackModel,
                persona = s.settings.persona,
                temperature = s.settings.temperature,
                maxTokens = s.settings.maxTokens,
                toolsEnabled = s.settings.toolsEnabled,
                selfVerify = s.settings.selfVerify,
                streaming = s.settings.streaming,
                maxIterations = s.settings.maxIterations,
                enabledTools = container.engine.allTools
                    .map { it.name }
                    .filterNot { it in s.settings.disabledTools }
                    .toSet()
            )

            val outcome = try {
                container.engine.run(input) { event -> handleEvent(event, cid) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(busy = false, status = null, streamingText = "") }
                    return@launch
                }
                null
            }

            if (outcome == null) {
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = "⚠️ The request failed unexpectedly. Tap retry.",
                        error = true
                    )
                )
            } else {
                repo.addMessage(
                    MessageEntity(
                        conversationId = cid,
                        role = "assistant",
                        content = outcome.answer,
                        model = outcome.model,
                        provider = outcome.provider.label,
                        verified = outcome.trace.verified,
                        verdict = outcome.trace.verdict,
                        error = outcome.error != null,
                        traceJson = outcome.trace.encode(),
                        promptTokens = outcome.usage?.promptTokens ?: 0,
                        completionTokens = outcome.usage?.completionTokens ?: 0,
                        latencyMs = System.currentTimeMillis() - started
                    )
                )
                outcome.trace.steps.forEach { st ->
                    if (st.artifactUri != null && st.tool.contains("file")) {
                        repo.recordArtifact(
                            ArtifactEntity(
                                conversationId = cid,
                                name = st.artifactName ?: "file",
                                uri = st.artifactUri,
                                mime = "*/*",
                                bytes = 0
                            )
                        )
                    }
                }
            }

            if (_state.value.settings.autoTitle) repo.maybeAutoTitle(cid, prompt)

            _state.update {
                it.copy(busy = false, status = null, streamingText = "", verifying = false, liveTraces = emptyList())
            }
        }
    }

    private fun handleEvent(event: AgentEvent, cid: Long) {
        when (event) {
            is AgentEvent.Status -> _state.update { it.copy(status = event.text) }
            is AgentEvent.Delta -> _state.update {
                it.copy(streamingText = it.streamingText + event.text, status = null)
            }
            is AgentEvent.Plan -> _state.update { it.copy(status = event.text) }
            is AgentEvent.ToolStart -> _state.update {
                it.copy(status = "Using ${event.tool}…")
            }
            is AgentEvent.ToolEnd -> _state.update {
                it.copy(liveTraces = it.liveTraces + event.trace, status = null)
            }
            is AgentEvent.ProviderSwitch -> _state.update {
                it.copy(status = "Switching to ${event.to} (${event.reason})")
            }
            AgentEvent.Verifying -> _state.update { it.copy(verifying = true, status = "Verifying answer…") }
            is AgentEvent.Verified -> _state.update { it.copy(verifying = false, status = null) }
            is AgentEvent.Artifact -> _state.update { it.copy(banner = "Saved ${event.name}") }
        }
    }

    /* ------------------------------ settings ------------------------------ */

    val settingsRepo get() = container.settingsRepo
    val toolNames get() = container.engine.allTools.map { it.name to it.description }

    fun modelLabel(id: String) = ModelCatalog.byId(id).label

    fun keyStatus(): Pair<Boolean, Boolean> =
        container.keyProvider.groqKey().isNotBlank() to container.keyProvider.openRouterKey().isNotBlank()

    suspend fun stats() = repo.stats()

    fun traceOf(m: MessageEntity): AgentTraceLog = AgentTraceLog.decode(m.traceJson)
}

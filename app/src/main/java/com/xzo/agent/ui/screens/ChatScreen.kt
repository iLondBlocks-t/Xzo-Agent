package com.xzo.agent.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.components.GradientBackground
import com.xzo.agent.ui.components.InputBar
import com.xzo.agent.ui.components.MarkdownText
import com.xzo.agent.ui.components.MessageBubble
import com.xzo.agent.ui.components.StreamingCaret
import com.xzo.agent.ui.components.ThinkingDots
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onMic: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onCamera: () -> Unit = {},
    onOpenConnect: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.streamingText, state.status) {
        val target = state.messages.size + if (state.busy) 1 else 0
        if (target > 0) listState.animateScrollToItem((target - 1).coerceAtLeast(0))
    }

    LaunchedEffect(state.banner) {
        if (state.banner != null) { delay(2600); vm.banner(null) }
    }

    GradientBackground(animated = state.settings.animatedBackground) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Rounded.Menu, "Chats")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                state.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                state.rateLimit?.summary()
                                    ?: vm.modelLabel(state.settings.primaryModel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenLibrary) {
                            Icon(Icons.Rounded.AutoAwesome, "Prompt library")
                        }
                        IconButton(onClick = onOpenModels) { Icon(Icons.Rounded.Tune, "Model") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("New chat") },
                                    onClick = { menuOpen = false; vm.newChat() })
                                DropdownMenuItem(
                                    text = { Text("Export as markdown") },
                                    onClick = { menuOpen = false; vm.exportCurrent() })
                                DropdownMenuItem(
                                    text = { Text("Export as PDF") },
                                    onClick = { menuOpen = false; vm.exportPdf() })
                                DropdownMenuItem(
                                    text = { Text("Clear messages") },
                                    onClick = { menuOpen = false; vm.clearCurrent() })
                                DropdownMenuItem(
                                    text = { Text("Prompt library") },
                                    onClick = { menuOpen = false; onOpenLibrary() })
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { menuOpen = false; onOpenSettings() })
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    AnimatedVisibility(
                        visible = state.banner != null,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200))
                    ) {
                        Text(
                            state.banner.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 4.dp)
                        )
                    }
                    if (state.lastError != null && !state.busy) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    state.lastError.take(120),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Retry",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clickable { vm.regenerate() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    ModeSelector(
                        mode = state.mode,
                        enabled = !state.busy,
                        onSelect = vm::setMode
                    )
                    InputBar(
                        value = state.input,
                        onValueChange = vm::onInputChange,
                        onSend = {
                            if (state.settings.haptics) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            vm.send()
                        },
                        onStop = vm::stop,
                        onAttach = { vm.attachFile() },
                        onAttachImage = { vm.attachFile(imagesOnly = true) },
                        onCamera = onCamera,
                        busy = state.busy,
                        attachments = state.attachments,
                        onRemoveAttachment = vm::removeAttachment,
                        enterSends = state.settings.enterSends,
                        voice = state.voice,
                        onMic = onMic
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (groqOk, orOk) = vm.keyStatus()
                if (!groqOk && !orOk) {
                    item { SetupNotice(onOpenSettings = onOpenConnect) }
                }

                if (state.messages.isEmpty() && !state.busy) {
                    item { EmptyState(onSuggestion = { vm.onInputChange(it) }) }
                }

                items(state.messages, key = { it.id }) { m ->
                    MessageBubble(
                        message = m,
                        trace = vm.traceOf(m),
                        showTrace = state.settings.showTrace,
                        onRetry = { vm.regenerate() },
                        onEdit = { vm.editMessage(m) },
                        onBranch = { vm.branchFrom(m) },
                        onSpeak = { vm.speak(m) },
                        speaking = state.speakingMessageId == m.id,
                        onShare = { text -> shareText(context, text) },
                        onOpenArtifact = { uri -> openUri(context, uri) }
                    )
                }

                if (state.busy) {
                    item { LiveTurn(state) }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    mode: com.xzo.agent.agent.AgentMode,
    enabled: Boolean,
    onSelect: (com.xzo.agent.agent.AgentMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        com.xzo.agent.agent.AgentMode.entries.forEach { m ->
            val selected = m == mode
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.clickable(enabled = enabled) { onSelect(m) }
            ) {
                Text(
                    m.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveTurn(state: ChatUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.plan?.let { plan ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Plan", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        plan,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        state.liveTraces.forEach { t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "${if (t.ok) "✓" else "✕"} ${t.tool}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    t.summary.take(60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (state.streamingText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(14.dp)) {
                    MarkdownText(state.streamingText)
                    Spacer(Modifier.size(4.dp))
                    StreamingCaret()
                }
            }
        }

        if (state.status != null || state.streamingText.isBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            ) {
                ThinkingDots(label = state.status ?: "Thinking…")
            }
        }
    }
}

@Composable
private fun SetupNotice(onOpenSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable { onOpenSettings() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Xzo is not connected yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            Text(
                "Tap here to paste an access key and test the connection. On-device features — reading " +
                    "photos and PDFs, offline translation, the calculator — already work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "Search the web for the top AI news today and summarise it with sources",
        "Create a CSV file with a 12-month budget template and save it to my phone",
        "Read a file I attach and explain what it does, line by line",
        "Compute the compound interest on 5,000 at 7% for 12 years",
        "ابحث عن أفضل هاتف بسعر أقل من 200 دولار واكتب مقارنة"
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Xzo Agent", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plan · Act · Observe · Verify",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(6.dp))
        suggestions.forEach { s ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable { onSuggestion(s) }
            ) {
                Text(
                    s,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
        Text(
            "Free and unlimited: Xzo never charges you, never shows ads and sets no quota of its own.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp)
        )
    }
}

private fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun openUri(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(uri), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}

/** Kept for potential reuse in previews/tests. */
fun previewMessage() = MessageEntity(id = 1, conversationId = 1, role = "assistant", content = "Hello")

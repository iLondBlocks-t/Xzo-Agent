package com.xzo.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.xzo.agent.agent.AgentTraceLog
import com.xzo.agent.agent.ToolTrace
import com.xzo.agent.data.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    trace: AgentTraceLog,
    showTrace: Boolean,
    onRetry: () -> Unit,
    onEdit: () -> Unit = {},
    onBranch: () -> Unit = {},
    onSpeak: () -> Unit = {},
    speaking: Boolean = false,
    onShare: (String) -> Unit,
    onOpenArtifact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    var expanded by remember(message.id) { mutableStateOf(false) }
    var sheetOpen by remember(message.id) { mutableStateOf(false) }
    var viewerUri by remember(message.id) { mutableStateOf<String?>(null) }
    var traceDialog by remember(message.id) { mutableStateOf<com.xzo.agent.agent.ToolTrace?>(null) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(260)) + slideInVertically(tween(280)) { it / 6 } + expandVertically(tween(240))
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp
                ),
                color = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                else
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = if (isUser) 0.dp else 2.dp,
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { sheetOpen = true }
                    )
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    val refs = remember(message.attachmentsJson) {
                        com.xzo.agent.agent.AttachmentRef.decode(message.attachmentsJson)
                    }
                    val images = refs.filter { it.image && it.uri.isNotBlank() }
                    if (images.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            images.take(3).forEach { ref ->
                                UriThumbnail(uri = ref.uri, size = 96.dp, onClick = { viewerUri = ref.uri })
                            }
                        }
                    }
                    if (!message.attachmentsJson.isNullOrBlank() && images.size != refs.size) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                refs.filterNot { it.image }.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    MarkdownText(
                        text = message.content,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Rounded.Edit, "Edit",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onBranch, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Rounded.CallSplit, "Branch from here",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp)
                ) {
                    VerifiedChip(visible = message.verified, verdict = message.verdict)

                    if (trace.steps.isNotEmpty() && showTrace) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    RoundedCornerShape(50)
                                )
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${trace.steps.size} tool ${if (trace.steps.size == 1) "call" else "calls"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(message.content)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy, "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onShare(message.content) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Share, "Share",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (speaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
                            if (speaking) "Stop reading" else "Read aloud",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Refresh, "Retry",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    TracePanel(trace, onOpenArtifact, onInspect = { traceDialog = it })
                }

                MetaLine(message, trace)
            }

            MessageDialogs(
                message = message,
                sheetOpen = sheetOpen,
                onSheetDismiss = { sheetOpen = false },
                onSpeak = onSpeak,
                onEdit = onEdit,
                onBranch = onBranch,
                onShare = onShare,
                onRetry = onRetry
            )

            viewerUri?.let { uri ->
                ImageViewerDialog(uri = uri, onDismiss = { viewerUri = null })
            }

            traceDialog?.let { tr ->
                ToolOutputDialog(trace = tr, onDismiss = { traceDialog = null })
            }
        }
    }
}

@Composable
private fun MessageDialogs(
    message: MessageEntity,
    sheetOpen: Boolean,
    onSheetDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onEdit: () -> Unit,
    onBranch: () -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    if (!sheetOpen) return
    val actions = buildList {
        add(SheetAction("Copy text", Icons.Rounded.ContentCopy) {
            clipboard.setText(AnnotatedString(message.content))
        })
        add(SheetAction("Share", Icons.Rounded.Share) { onShare(message.content) })
        if (message.role == "assistant") {
            add(SheetAction("Read aloud", Icons.Rounded.VolumeUp, onSpeak))
            add(SheetAction("Regenerate", Icons.Rounded.Refresh, onRetry))
        } else {
            add(SheetAction("Edit & resend", Icons.Rounded.Edit, onEdit))
            add(SheetAction("Branch from here", Icons.Rounded.CallSplit, onBranch))
        }
    }
    ActionSheetDialog(
        title = message.content.take(60).replace("\n", " "),
        actions = actions,
        onDismiss = onSheetDismiss
    )
}

@Composable
private fun MetaLine(message: MessageEntity, trace: AgentTraceLog) {
    val time = remember(message.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
    }
    val bits = buildList {
        add(time)
        message.model?.takeIf { it.isNotBlank() }?.let {
            add(com.xzo.agent.data.remote.ModelCatalog.byId(it).label)
        }
        if (trace.fallbackUsed) add("fallback")
        if (message.completionTokens > 0) add("${message.promptTokens + message.completionTokens} tok")
        if (message.latencyMs > 0) add("${message.latencyMs / 100 / 10.0}s")
    }
    Text(
        bits.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 3.dp, start = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun TracePanel(
    trace: AgentTraceLog,
    onOpenArtifact: (String) -> Unit,
    onInspect: (ToolTrace) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Agent trace · ${trace.iterations} iteration(s) · ${trace.provider ?: "?"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        trace.steps.forEachIndexed { i, s ->
            Column(Modifier.clickable { onInspect(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        com.xzo.agent.ui.components.XzoIcons.forTool(s.tool.lowercase()),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (s.ok) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${i + 1}. ${s.tool}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${s.durationMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    s.argsPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 14.dp)
                )
                Text(
                    s.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = 14.dp)
                )
                s.artifactUri?.let { uri ->
                    Text(
                        "Open ${s.artifactName ?: "file"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 14.dp, top = 2.dp)
                            .clickable { onOpenArtifact(uri) }
                    )
                }
            }
        }
        trace.plan?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Plan:\n$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trace.reasoning?.takeIf { it.isNotBlank() }?.let { r ->
            var open by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { open = !open }
            ) {
                Text(
                    "Model reasoning",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (open) {
                Text(
                    r,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
        trace.verdict?.takeIf { it.isNotBlank() && it != "none" }?.let {
            Text(
                "Reviewer: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

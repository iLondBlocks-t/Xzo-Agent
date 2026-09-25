package com.xzo.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xzo.agent.agent.ToolTrace
import com.xzo.agent.ui.theme.XzoFonts

/** Full-screen viewer for an attached picture. */
@Composable
fun ImageViewerDialog(uri: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            UriThumbnail(uri = uri, size = 340.dp, corner = 18.dp)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Rounded.Close, "Close", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

/** Shows the complete, untruncated output of one tool call. */
@Composable
fun ToolOutputDialog(trace: ToolTrace, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        XzoIcons.forTool(trace.tool.lowercase()),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(trace.tool, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(trace.fullOutput.ifBlank { trace.summary }))
                    }) {
                        Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(16.dp))
                    }
                }
                Text(
                    "${trace.durationMs} ms" + if (trace.ok) "" else " · failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trace.argsPreview.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text("Arguments", style = MaterialTheme.typography.labelMedium)
                    Text(
                        trace.argsPreview,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = XzoFonts.Mono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text("Result", style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        trace.fullOutput.ifBlank { trace.summary },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

data class SheetAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Long-press menu for a message. */
@Composable
fun ActionSheetDialog(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
                actions.forEach { action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action.onClick(); onDismiss() }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(action.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

internal val TransparentScrim = Color(0x00000000)

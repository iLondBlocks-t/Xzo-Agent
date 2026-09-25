package com.xzo.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatDrawer(
    state: ChatUiState,
    vm: ChatViewModel,
    onSelect: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
    onOpenAutomations: () -> Unit = {}
) {
    val t = com.xzo.agent.ui.LocalStrings.current
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(310.dp).fillMaxHeight()
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Xzo Agent", style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = { vm.newChat() }) { Icon(Icons.Rounded.Add, t.newChat) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, t.settings) }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.search(it) },
                placeholder = { Text(t.searchMessages) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerPill(t.promptLibrary, Modifier.weight(1f), onOpenLibrary, Icons.Rounded.AutoAwesomeMotion)
                DrawerPill(t.workspace, Modifier.weight(1f), onOpenWorkspace, Icons.Rounded.FolderOpen)
            }
            Spacer(Modifier.height(8.dp))
            DrawerPill(t.automations, Modifier.fillMaxWidth(), onOpenAutomations, Icons.Rounded.Schedule)

            Spacer(Modifier.height(10.dp))

            if (state.searchQuery.isNotBlank()) {
                Text(
                    "${state.searchResults.size} result(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.searchResults, key = { it.id }) { m ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.openSearchResult(m); onSelect(m.conversationId) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                m.content.take(90),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.conversations, key = { it.id }) { c ->
                        val selected = c.id == state.conversationId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelect(c.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(c.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { vm.pinChat(c.id, !c.pinned) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PushPin, "Pin",
                                    modifier = Modifier.size(15.dp),
                                    tint = if (c.pinned) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            IconButton(
                                onClick = { vm.deleteChat(c.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete, "Delete",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

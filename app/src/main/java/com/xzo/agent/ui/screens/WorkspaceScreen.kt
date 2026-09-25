package com.xzo.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.components.GradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Files the agent created + long-term memory + usage statistics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(state: ChatUiState, vm: ChatViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var memories by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var stats by remember { mutableStateOf(Triple(0, 0, 0)) }

    LaunchedEffect(tab) {
        memories = vm.listMemories()
        stats = vm.stats()
    }

    GradientBackground(animated = state.settings.animatedBackground) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Workspace") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    listOf("Files", "Memory", "Usage").forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }

                when (tab) {
                    0 -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.artifacts.isEmpty()) {
                            item {
                                Text(
                                    "No files yet. Ask Xzo to create one — for example “make a CSV budget and save it”.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(state.artifacts, key = { it.id }) { a ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable { openUri(context, a.uri) }
                                    ) {
                                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
                                                .format(Date(a.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.deleteArtifact(a.id) }) {
                                        Icon(Icons.Rounded.Delete, "Remove", Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    1 -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Facts Xzo remembers across every chat. They are injected into the system prompt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(memories, key = { it.first }) { (k, v) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(k, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            v,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        vm.forgetMemory(k)
                                        memories = memories.filterNot { it.first == k }
                                    }) {
                                        Icon(Icons.Rounded.Delete, "Forget", Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (memories.isEmpty()) {
                            item {
                                Text(
                                    "Nothing remembered yet. Try: “remember that I prefer answers in Arabic”.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    else -> Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard("Conversations", stats.first.toString())
                        StatCard("Messages", stats.second.toString())
                        StatCard("Tokens used", stats.third.toString())
                        StatCard("Files created", state.artifacts.size.toString())
                        StatCard("Primary model", vm.modelLabel(state.settings.primaryModel))
                        StatCard("Fallback model", vm.modelLabel(state.settings.fallbackModel))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Xzo never charges you and sets no quota of its own. When a compute route is saturated it " +
                                "backs off, retries and switches to a backup route automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun openUri(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}

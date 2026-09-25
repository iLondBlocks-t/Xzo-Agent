package com.xzo.agent.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xzo.agent.data.db.AutomationEntity
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.components.GradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scheduled agent tasks: "every morning at 07:30, search the news and summarise it",
 * "every evening, check these prices". They run in the background through
 * WorkManager and arrive as a notification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(state: ChatUiState, vm: ChatViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<AutomationEntity?>(null) }

    GradientBackground(animated = state.settings.animatedBackground) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Automations") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    editing = AutomationEntity(title = "", prompt = "", hour = 8, minute = 0)
                }) { Icon(Icons.Rounded.Add, "New automation") }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Xzo can work while you sleep. Each automation runs its prompt with the full " +
                            "agent loop at the time you choose, saves the answer to its own chat, and " +
                            "sends you a notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.automations.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            Text("Try one of these", style = MaterialTheme.typography.labelLarge)
                            SUGGESTIONS.forEach { (title, prompt) ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        editing = AutomationEntity(title = title, prompt = prompt, hour = 8)
                                    }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(title, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(state.automations, key = { it.id }) { a ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { editing = a }) {
                                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Daily at %02d:%02d · %s".format(a.hour, a.minute, a.mode.lowercase()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = a.enabled,
                                    onCheckedChange = { vm.toggleAutomation(a, it) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                a.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3
                            )
                            if (a.lastRunAt > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${if (a.lastOk) "✓" else "✕"} last run " +
                                        SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                                            .format(Date(a.lastRunAt)) +
                                        " · " + (a.lastResult?.take(70).orEmpty()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 2
                                )
                            }
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { vm.runAutomationNow(a) }) {
                                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Run now")
                                }
                                if (a.conversationId > 0) {
                                    TextButton(onClick = { vm.openAutomationChat(a); onBack() }) {
                                        Text("Open chat")
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteAutomation(a) }) {
                                    Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    editing?.let { draft ->
        AutomationEditor(
            draft = draft,
            onDismiss = { editing = null },
            onSave = {
                vm.saveAutomation(it)
                editing = null
            }
        )
    }
}

@Composable
private fun AutomationEditor(
    draft: AutomationEntity,
    onDismiss: () -> Unit,
    onSave: (AutomationEntity) -> Unit
) {
    var title by remember { mutableStateOf(draft.title) }
    var prompt by remember { mutableStateOf(draft.prompt) }
    var hour by remember { mutableStateOf(draft.hour.toFloat()) }
    var minute by remember { mutableStateOf(draft.minute.toFloat()) }
    var mode by remember { mutableStateOf(draft.mode) }
    var notify by remember { mutableStateOf(draft.notify) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == 0L) "New automation" else "Edit automation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("What should Xzo do?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Run at %02d:%02d".format(hour.toInt(), minute.toInt()))
                Slider(value = hour, onValueChange = { hour = it }, valueRange = 0f..23f, steps = 22)
                Slider(value = minute, onValueChange = { minute = it }, valueRange = 0f..55f, steps = 10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AGENT", "DEEP_RESEARCH", "CHAT").forEach { m ->
                        TextButton(onClick = { mode = m }) {
                            Text(
                                if (mode == m) "● ${m.lowercase()}" else m.lowercase(),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = notify, onCheckedChange = { notify = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Notify me with the result", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && prompt.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            title = title.trim(),
                            prompt = prompt.trim(),
                            hour = hour.toInt(),
                            minute = minute.toInt(),
                            mode = mode,
                            notify = notify,
                            enabled = true
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val SUGGESTIONS = listOf(
    "Morning briefing" to
        "Search the web for the most important technology and AI news from the last 24 hours " +
        "and give me 6 bullets with source links. Then add one line on what it means for me.",
    "Price watch" to
        "Search for the current best price of [PRODUCT] and tell me if it dropped compared to " +
        "the typical price, with the links.",
    "Daily plan" to
        "Check today's date, look at my remembered goals, and write me a focused 3-task plan " +
        "for today with time blocks.",
    "ملخص الصباح" to
        "ابحث عن أهم أخبار التقنية والذكاء الاصطناعي خلال آخر 24 ساعة ولخصها في ست نقاط مع روابط المصادر."
)

package com.xzo.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xzo.agent.BuildConfig
import com.xzo.agent.core.ThemeMode
import com.xzo.agent.data.remote.ModelCatalog
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.components.GradientBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: ChatUiState, vm: ChatViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s = state.settings
    val repo = vm.settingsRepo
    val (groqOk, orOk) = vm.keyStatus()
    var stats by remember { mutableStateOf(Triple(0, 0, 0)) }
    LaunchedEffect(Unit) { stats = vm.stats() }

    GradientBackground(animated = s.animatedBackground) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                item {
                    Card("Models") {
                        Text(
                            "Primary model",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ModelCatalog.groq.forEach { m ->
                            SelectableRow(
                                title = m.label,
                                subtitle = m.description,
                                selected = s.primaryModel == m.id
                            ) { scope.launch { repo.setPrimaryModel(m.id) } }
                        }
                        ModelCatalog.openRouter.forEach { m ->
                            SelectableRow(
                                title = m.label,
                                subtitle = m.description,
                                selected = s.primaryModel == m.id
                            ) { scope.launch { repo.setPrimaryModel(m.id) } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Fallback model (used on error / 429)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ModelCatalog.openRouter.forEach { m ->
                            SelectableRow(
                                title = m.label,
                                subtitle = m.description,
                                selected = s.fallbackModel == m.id
                            ) { scope.launch { repo.setFallbackModel(m.id) } }
                        }
                    }
                }

                item {
                    Card("Agent behaviour") {
                        ToggleRow("Tool use", "Let the agent search, run code and write files", s.toolsEnabled) {
                            scope.launch { repo.setToolsEnabled(it) }
                        }
                        ToggleRow("Self-verification", "Second pass that checks the answer before showing it", s.selfVerify) {
                            scope.launch { repo.setSelfVerify(it) }
                        }
                        ToggleRow("Streaming", "Show tokens as they arrive", s.streaming) {
                            scope.launch { repo.setStreaming(it) }
                        }
                        ToggleRow("Show agent trace", "Expandable list of every tool call", s.showTrace) {
                            scope.launch { repo.setShowTrace(it) }
                        }
                        ToggleRow("Auto-title chats", "Name conversations from the first message", s.autoTitle) {
                            scope.launch { repo.setAutoTitle(it) }
                        }
                        ToggleRow("Enter key sends", "Otherwise Enter inserts a newline", s.enterSends) {
                            scope.launch { repo.setEnterSends(it) }
                        }

                        SliderRow("Temperature", s.temperature.toFloat(), 0f, 1.5f, "%.2f") {
                            scope.launch { repo.setTemperature(it.toDouble()) }
                        }
                        SliderRow("Max tokens", s.maxTokens.toFloat(), 256f, 8192f, "%.0f") {
                            scope.launch { repo.setMaxTokens(it.toInt()) }
                        }
                        SliderRow("Max tool iterations", s.maxIterations.toFloat(), 1f, 12f, "%.0f") {
                            scope.launch { repo.setMaxIterations(it.toInt()) }
                        }
                        SliderRow("History window (messages)", s.historyWindow.toFloat(), 4f, 60f, "%.0f") {
                            scope.launch { repo.setHistoryWindow(it.toInt()) }
                        }
                    }
                }

                item {
                    Card("Tools") {
                        vm.toolNames.forEach { (name, desc) ->
                            ToggleRow(name, desc.take(90), name !in s.disabledTools) { enabled ->
                                scope.launch { repo.toggleTool(name, enabled) }
                            }
                        }
                    }
                }

                item {
                    Card("Persona / system prompt") {
                        var text by remember(s.persona) { mutableStateOf(s.persona) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("How Xzo should behave") }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { repo.setPersona(text) } }) { Text("Save") }
                            Button(onClick = {
                                text = com.xzo.agent.agent.Prompts.DEFAULT_PERSONA
                                scope.launch { repo.setPersona(text) }
                            }) { Text("Reset") }
                        }
                    }
                }

                item {
                    Card("Appearance") {
                        ThemeMode.entries.forEach { mode ->
                            SelectableRow(
                                title = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                subtitle = when (mode) {
                                    ThemeMode.SYSTEM -> "Follow the device light/dark setting"
                                    ThemeMode.LIGHT -> "White / light gray gradient"
                                    ThemeMode.DARK -> "Near-black / gray gradient"
                                },
                                selected = s.themeMode == mode
                            ) { scope.launch { repo.setThemeMode(mode) } }
                        }
                        ToggleRow("Animated background", "Slow drifting gradient", s.animatedBackground) {
                            scope.launch { repo.setAnimatedBackground(it) }
                        }
                        ToggleRow("Haptics", "Subtle vibration on send", s.haptics) {
                            scope.launch { repo.setHaptics(it) }
                        }
                    }
                }

                item {
                    Card("API keys") {
                        Text(
                            "Keys normally come from GitHub Secrets baked into the build. " +
                                "You can override them here; overrides stay on this device only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusRow("Groq key", groqOk, BuildConfig.GROQ_API_KEY.isNotBlank())
                        StatusRow("OpenRouter key", orOk, BuildConfig.OPENROUTER_API_KEY.isNotBlank())
                        Spacer(Modifier.height(8.dp))

                        var groq by remember(s.groqKeyOverride) { mutableStateOf(s.groqKeyOverride) }
                        OutlinedTextField(
                            value = groq,
                            onValueChange = { groq = it },
                            label = { Text("Groq API key override") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        var or by remember(s.openRouterKeyOverride) { mutableStateOf(s.openRouterKeyOverride) }
                        OutlinedTextField(
                            value = or,
                            onValueChange = { or = it },
                            label = { Text("OpenRouter API key override") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch { repo.setGroqKey(groq); repo.setOpenRouterKey(or) }
                            vm.banner("Keys saved on device")
                        }) { Text("Save keys") }
                    }
                }

                item {
                    Card("About") {
                        Text("Xzo Agent ${BuildConfig.VERSION_NAME} · arm32 · Android 9+", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "“Unlimited” means this app imposes no cost, quota, ads, login or paywall. " +
                                "Your actual throughput is still governed by the free-tier rate limits of Groq " +
                                "and OpenRouter; when they return HTTP 429 the app backs off, retries and " +
                                "automatically falls back to the other provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Local data: ${stats.first} chats · ${stats.second} messages · ${stats.third} tokens used",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    format: String,
    onChange: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(format.format(local), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = min..max
        )
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean, fromBuild: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(4.dp)
                )
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "$label: ${if (active) "configured" else "missing"}" +
                if (fromBuild) " (from build secrets)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

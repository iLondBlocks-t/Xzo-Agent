package com.xzo.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xzo.agent.data.remote.LlmClient
import com.xzo.agent.data.remote.Provider
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.components.GradientBackground

/**
 * Connection setup.
 *
 * The single most common failure for a self-built app is a key that is missing,
 * half-pasted or revoked — and a bare "API key rejected" tells the user nothing.
 * This screen pastes, saves and **live-tests** the key, then says exactly what is
 * wrong and how to fix it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onDone: () -> Unit,
    showBack: Boolean = true
) {
    val t = com.xzo.agent.ui.LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var primary by remember(state.settings.groqKeyOverride) { mutableStateOf(state.settings.groqKeyOverride) }
    var backup by remember(state.settings.openRouterKeyOverride) { mutableStateOf(state.settings.openRouterKeyOverride) }

    GradientBackground(animated = state.settings.animatedBackground) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(t.connectTitle) },
                    navigationIcon = {
                        if (showBack) IconButton(onClick = onDone) { Icon(Icons.Rounded.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                Text(t.connectLead, style = MaterialTheme.typography.titleMedium)
                Text(
                    t.connectPrivacy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                KeyCard(
                    title = t.primaryKey,
                    subtitle = t.primaryKeyHint,
                    value = primary,
                    onValueChange = { primary = it },
                    onPaste = { clipboard.getText()?.text?.let { primary = it.trim() } },
                    check = state.keyChecks[Provider.GROQ],
                    busy = state.checkingKey == Provider.GROQ,
                    onSave = { vm.saveAndTestKey(Provider.GROQ, primary) }
                )

                KeyCard(
                    title = t.backupKey,
                    subtitle = t.backupKeyHint,
                    value = backup,
                    onValueChange = { backup = it },
                    onPaste = { clipboard.getText()?.text?.let { backup = it.trim() } },
                    check = state.keyChecks[Provider.OPENROUTER],
                    busy = state.checkingKey == Provider.OPENROUTER,
                    onSave = { vm.saveAndTestKey(Provider.OPENROUTER, backup) }
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t.troubleTitle, style = MaterialTheme.typography.labelLarge)
                        (if (t.rtl) com.xzo.agent.ui.troubleTipsAr else com.xzo.agent.ui.troubleTipsEn).forEach {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier
                                        .padding(top = 6.dp, end = 8.dp)
                                        .size(4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.offlineTitle, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t.offlineBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text(if (vm.keyStatus().let { it.first || it.second }) t.startUsing else t.continueOffline)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    check: LlmClient.KeyCheck?,
    busy: Boolean,
    onSave: () -> Unit
) {
    val t = com.xzo.agent.ui.LocalStrings.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusDot(check, busy)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(t.pasteKey) },
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Rounded.ContentPaste, "Paste from clipboard", Modifier.size(18.dp))
                    }
                },
                supportingText = {
                    Text(
                        if (value.isBlank()) t.notSet else "${value.length}",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = !busy) {
                    Text(if (busy) t.testing else t.saveAndTest)
                }
                if (value.isNotBlank()) {
                    OutlinedButton(onClick = { onValueChange("") }, enabled = !busy) { Text(t.clear) }
                }
            }

            check?.let { c ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (c.ok) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (c.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                when (c.state) {
                                    LlmClient.KeyState.OK -> t.stateConnected
                                    LlmClient.KeyState.MISSING -> t.stateMissing
                                    LlmClient.KeyState.MALFORMED -> t.stateMalformed
                                    LlmClient.KeyState.REJECTED -> t.stateRejected
                                    LlmClient.KeyState.RATE_LIMITED -> t.stateBusy
                                    LlmClient.KeyState.OFFLINE -> t.stateOffline
                                    LlmClient.KeyState.UNKNOWN -> t.stateUnknown
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                c.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(check: LlmClient.KeyCheck?, busy: Boolean) {
    when {
        busy -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> Box(
            Modifier
                .size(10.dp)
                .background(
                    color = when (check?.state) {
                        LlmClient.KeyState.OK -> MaterialTheme.colorScheme.onSurface
                        null -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    shape = CircleShape
                )
        )
    }
}

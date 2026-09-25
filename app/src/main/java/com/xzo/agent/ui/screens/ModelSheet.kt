package com.xzo.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xzo.agent.data.remote.ModelSpec
import com.xzo.agent.ui.ChatUiState
import com.xzo.agent.ui.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(state: ChatUiState, vm: ChatViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Choose a model", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick the brain Xzo should think with. If a route is busy, Xzo switches to a backup automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            androidx.compose.material3.TextButton(onClick = { vm.refreshModels() }) {
                Text(if (state.refreshingModels) "Refreshing…" else "Refresh live model list")
            }

            val groqModels = state.models.filter { it.provider == com.xzo.agent.data.remote.Provider.GROQ }
            val orModels = state.models.filter { it.provider == com.xzo.agent.data.remote.Provider.OPENROUTER }

            Text("Main routes", style = MaterialTheme.typography.labelLarge)
            groqModels.forEach { m -> ModelRow(m, state.settings.primaryModel == m.id) { pick(vm, scope, m) } }

            Spacer(Modifier.height(10.dp))
            Text("Backup routes", style = MaterialTheme.typography.labelLarge)
            orModels.forEach { m -> ModelRow(m, state.settings.primaryModel == m.id) { pick(vm, scope, m) } }
        }
    }
}

private fun pick(vm: ChatViewModel, scope: kotlinx.coroutines.CoroutineScope, m: ModelSpec) {
    scope.launch { vm.settingsRepo.setPrimaryModel(m.id) }
}

@Composable
private fun ModelRow(m: ModelSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, style = MaterialTheme.typography.bodyLarge)
                if (m.serverSideTools) {
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        Icons.Rounded.Bolt, contentDescription = "Agentic",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                m.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${m.id} · ${m.contextTokens / 1024}k ctx",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp))
    }
}

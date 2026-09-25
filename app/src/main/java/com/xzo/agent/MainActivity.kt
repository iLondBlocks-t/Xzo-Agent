package com.xzo.agent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.xzo.agent.agent.FileBridge
import com.xzo.agent.ui.ChatViewModel
import com.xzo.agent.ui.screens.ChatDrawer
import com.xzo.agent.ui.screens.ChatScreen
import com.xzo.agent.ui.screens.ModelSheet
import com.xzo.agent.ui.screens.SettingsScreen
import com.xzo.agent.ui.theme.XzoTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    private var pendingRequestId: Long = -1
    private lateinit var bridge: FileBridge

    private val createDocLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            bridge.deliver(pendingRequestId, uri)
        }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.onMicTap(true)
        }

    private val openDocLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            bridge.deliver(pendingRequestId, uri)
        }

    private val openTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            bridge.deliver(pendingRequestId, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        bridge = (application as XzoApp).container.files

        // Bridge SAF requests coming from the agent tools.
        lifecycleScope.launch {
            bridge.requests.collectLatest { req ->
                pendingRequestId = req.id
                when (req) {
                    is FileBridge.Request.Create -> runCatching {
                        createDocLauncher.launch(req.suggestedName)
                    }.onFailure { bridge.deliver(req.id, null) }

                    is FileBridge.Request.Open -> runCatching {
                        openDocLauncher.launch(req.mimes)
                    }.onFailure { bridge.deliver(req.id, null) }

                    is FileBridge.Request.OpenTree -> runCatching {
                        openTreeLauncher.launch(null)
                    }.onFailure { bridge.deliver(req.id, null) }
                }
            }
        }

        lifecycleScope.launch {
            vm.micPermissionRequests.collectLatest {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }

        val shared = intent?.let { readSharedText(it) }

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()

            LaunchedEffect(shared) {
                if (!shared.isNullOrBlank()) vm.onInputChange(shared)
            }

            XzoTheme(themeMode = state.settings.themeMode) {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showSettings by remember { mutableStateOf(false) }
                var showModels by remember { mutableStateOf(false) }
                var showLibrary by remember { mutableStateOf(false) }
                var showWorkspace by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(state = state, vm = vm, onBack = { showSettings = false })
                } else if (showLibrary) {
                    com.xzo.agent.ui.screens.LibraryScreen(
                        animatedBackground = state.settings.animatedBackground,
                        onPick = { prompt ->
                            vm.onInputChange(prompt)
                            showLibrary = false
                        },
                        onBack = { showLibrary = false }
                    )
                } else if (showWorkspace) {
                    com.xzo.agent.ui.screens.WorkspaceScreen(
                        state = state, vm = vm, onBack = { showWorkspace = false }
                    )
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ChatDrawer(
                                state = state,
                                vm = vm,
                                onSelect = { id ->
                                    vm.select(id)
                                    scope.launch { drawerState.close() }
                                },
                                onOpenSettings = {
                                    showSettings = true
                                    scope.launch { drawerState.close() }
                                },
                                onOpenLibrary = {
                                    showLibrary = true
                                    scope.launch { drawerState.close() }
                                },
                                onOpenWorkspace = {
                                    showWorkspace = true
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    ) {
                        ChatScreen(
                            state = state,
                            vm = vm,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenSettings = { showSettings = true },
                            onOpenModels = { showModels = true },
                            onMic = { vm.onMicTap(hasMicPermission()) },
                            onOpenLibrary = { showLibrary = true }
                        )
                    }

                    if (showModels) {
                        ModelSheet(state = state, vm = vm, onDismiss = { showModels = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSharedText(intent)?.let { vm.onInputChange(it) }
    }

    private fun hasMicPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun readSharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
    }
}

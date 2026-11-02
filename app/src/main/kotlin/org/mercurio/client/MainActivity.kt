package org.mercurio.client

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableSharedFlow
import org.mercurio.client.feature.entry.presentation.EntryScreen
import org.mercurio.client.feature.entry.presentation.EntryStateHolder
import org.mercurio.client.feature.entry.presentation.EntryUiEvent
import org.mercurio.client.feature.entry.presentation.HermesTheme
import org.mercurio.client.feature.entry.wiring.EntryWiring

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var entryStateHolder: EntryStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        entryStateHolder =
            EntryWiring.createEntryStateHolder(
                context = applicationContext,
                requestRunStatusNotificationPermission = { notificationPermissionRequests.tryEmit(Unit) },
            )
        setContent {
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    entryStateHolder.onEvent(EntryUiEvent.RunStatusNotificationPermissionResult(granted))
                }
            LaunchedEffect(notificationPermissionLauncher) {
                notificationPermissionRequests.collect {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val state by entryStateHolder.uiState.collectAsState()
            HermesTheme {
                EntryScreen(
                    state = state,
                    onEvent = entryStateHolder::onEvent,
                )
            }
        }
    }

    override fun onDestroy() {
        entryStateHolder.close()
        super.onDestroy()
    }
}

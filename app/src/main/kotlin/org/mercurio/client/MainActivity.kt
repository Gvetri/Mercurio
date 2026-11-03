package org.mercurio.client

import android.Manifest
import android.content.Intent
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
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.presentation.EntryScreen
import org.mercurio.client.feature.entry.presentation.EntryStateHolder
import org.mercurio.client.feature.entry.presentation.EntryUiEvent
import org.mercurio.client.feature.entry.presentation.HermesTheme
import org.mercurio.client.feature.entry.presentation.MessageImageProvider
import org.mercurio.client.feature.entry.wiring.EntryWiring

class MainActivity : ComponentActivity() {
    private val notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var entryStateHolder: EntryStateHolder
    private lateinit var messageImagePort: MessageImagePort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        entryStateHolder =
            EntryWiring.createEntryStateHolder(
                context = applicationContext,
                requestRunStatusNotificationPermission = { notificationPermissionRequests.tryEmit(Unit) },
            )
        messageImagePort = EntryWiring.createMessageImagePort()
        applyInboundLink(intent)
        applySharedText(intent)
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
                MessageImageProvider(loadImage = messageImagePort::fetch, autoLoad = state.autoLoadImages) {
                    EntryScreen(
                        state = state,
                        onEvent = entryStateHolder::onEvent,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        entryStateHolder.onEvent(EntryUiEvent.AppEnteredForeground)
    }

    override fun onStop() {
        super.onStop()
        entryStateHolder.onEvent(EntryUiEvent.AppEnteredBackground)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyInboundLink(intent)
        applySharedText(intent)
    }

    override fun onDestroy() {
        entryStateHolder.close()
        super.onDestroy()
    }

    private fun applyInboundLink(inbound: Intent?) {
        entryStateHolder.onEvent(EntryUiEvent.InboundUri(inbound?.data?.toString()))
    }

    private fun applySharedText(inbound: Intent?) {
        val share = inbound ?: return
        val action = share.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val text =
            share.getStringExtra(Intent.EXTRA_TEXT)
                ?: share.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        entryStateHolder.onEvent(
            EntryUiEvent.SharedText(
                mimeType = share.type,
                text = text,
                hasStream = share.hasExtra(Intent.EXTRA_STREAM),
                isMultiple = action == Intent.ACTION_SEND_MULTIPLE,
            ),
        )
    }
}

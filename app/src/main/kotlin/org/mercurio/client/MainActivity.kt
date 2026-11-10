package org.mercurio.client

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.presentation.EntryScreen
import org.mercurio.client.feature.entry.presentation.EntryStateHolder
import org.mercurio.client.feature.entry.presentation.EntryUiEvent
import org.mercurio.client.feature.entry.presentation.HermesTheme
import org.mercurio.client.feature.entry.presentation.MessageImageProvider
import org.mercurio.client.feature.entry.wiring.EntryHostActions
import org.mercurio.client.feature.entry.wiring.EntryWiring
import kotlin.system.exitProcess
import org.mercurio.client.feature.entry.presentation.R as PresentationR

class MainActivity : FragmentActivity() {
    private val notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val appUnlockRequests = Channel<Unit>(Channel.CONFLATED)
    private lateinit var entryStateHolder: EntryStateHolder
    private lateinit var messageImagePort: MessageImagePort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        entryStateHolder =
            EntryWiring.createEntryStateHolder(
                context = applicationContext,
                hostActions =
                    EntryHostActions(
                        requestRunStatusNotificationPermission = { notificationPermissionRequests.tryEmit(Unit) },
                        requestAppUnlock = { appUnlockRequests.trySend(Unit) },
                        requestInstallPermissionSettings = { openInstallPermissionSettings() },
                        requestAppRestart = { restartApplication() },
                    ),
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
            LaunchedEffect(appUnlockRequests) {
                appUnlockRequests.receiveAsFlow().collect { promptForAppUnlock() }
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
        val text = (share.extras?.get(Intent.EXTRA_TEXT) as? CharSequence)?.toString()
        entryStateHolder.onEvent(
            EntryUiEvent.SharedText(
                mimeType = share.type,
                text = text,
                hasStream = share.hasExtra(Intent.EXTRA_STREAM),
                isMultiple = action == Intent.ACTION_SEND_MULTIPLE,
            ),
        )
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
        )
    }

    private fun restartApplication() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        exitProcess(0)
    }

    private fun promptForAppUnlock() {
        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        entryStateHolder.onEvent(EntryUiEvent.AppLockAuthenticationResult(authenticated = true))
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        entryStateHolder.onEvent(EntryUiEvent.AppLockAuthenticationResult(authenticated = false))
                    }
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(PresentationR.string.settings_app_lock_row))
                .setSubtitle(getString(PresentationR.string.settings_app_lock_supporting))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }
}

package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EntryScreen(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        ) {
            val sessionList = state.sessionList
            if (state.localDiagnostics.isOpen) {
                LocalDiagnosticsContent(
                    state = state.localDiagnostics,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (sessionList != null && !state.isChangingCredential) {
                SessionShell(
                    state = sessionList,
                    runStatusNotifications = state.runStatusNotifications,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxSize().padding(LocalHermesDesignTokens.current.spacing.xl),
                )
            } else {
                ConnectionContent(state = state, onEvent = onEvent)
            }
            if (state.isRemoveConfirmationOpen) {
                RemoveGatewayConnectionConfirmation(onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ConnectionContent(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val showConnectionForm = state.isChangingCredential || (state.connectionSetupRequested && !state.isConnected)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(if (showConnectionForm) Modifier.imePadding() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(LocalHermesDesignTokens.current.spacing.xl),
        verticalArrangement = if (showConnectionForm) Arrangement.Top else Arrangement.Center,
        horizontalAlignment = if (showConnectionForm) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (!showConnectionForm) {
            Text(
                text = "Hermes Native Client",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = if (showConnectionForm) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = state.supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (showConnectionForm) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        when {
            state.isChangingCredential -> GatewayConnectionForm(state = state, onEvent = onEvent)
            state.isConnected -> ConnectedGatewayContent(onEvent)
            state.connectionSetupRequested ->
                GatewayConnectionForm(
                    state = state,
                    onEvent = onEvent,
                )
            else ->
                Button(
                    onClick = { onEvent(EntryUiEvent.AddGatewayConnectionClicked) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                ) {
                    Text(text = state.actionLabel)
                }
        }
    }
}

@Composable
private fun GatewayConnectionForm(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    var credentialVisible by remember(state.isChangingCredential) { mutableStateOf(false) }
    val isPending = state.isVerifying || state.isSaving
    if (state.isChangingCredential) {
        Text(
            text = "Gateway HTTPS endpoint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.endpoint,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        val endpointError = state.errorCategory == EntryErrorCategory.INVALID_ADDRESS
        OutlinedTextField(
            value = state.endpoint,
            onValueChange = { onEvent(EntryUiEvent.EndpointChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isPending,
            isError = endpointError,
            label = { Text("Gateway HTTPS endpoint") },
            supportingText = {
                Text(
                    text =
                        if (endpointError) {
                            EntryErrorCategory.INVALID_ADDRESS.safeMessage
                        } else {
                            "Example: https://gateway.example"
                        },
                    modifier =
                        if (endpointError) {
                            Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        } else {
                            Modifier
                        },
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    val credentialError = state.errorCategory == EntryErrorCategory.AUTHENTICATION_FAILED
    OutlinedTextField(
        value = state.bearerCredential,
        onValueChange = { onEvent(EntryUiEvent.BearerCredentialChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isPending,
        isError = credentialError,
        label = { Text("Bearer credential") },
        singleLine = true,
        visualTransformation = if (credentialVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(
                onClick = { credentialVisible = !credentialVisible },
                enabled = !isPending,
                modifier =
                    Modifier.semantics {
                        contentDescription = if (credentialVisible) "Hide credential" else "Show credential"
                    },
            ) {
                Text(text = if (credentialVisible) "Hide" else "Show")
            }
        },
        supportingText =
            if (credentialError) {
                {
                    Text(
                        text = EntryErrorCategory.AUTHENTICATION_FAILED.safeMessage,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            } else {
                null
            },
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text =
            if (state.isChangingCredential) {
                "The current connection stays in place until the replacement is verified and saved securely."
            } else {
                "The credential is saved securely on this device after verification."
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    val endpointSlotError = !state.isChangingCredential && state.errorCategory == EntryErrorCategory.INVALID_ADDRESS
    val standaloneError = state.errorCategory?.takeUnless { endpointSlotError || credentialError }
    val completionLine =
        when {
            state.errorCategory == null -> null
            state.isChangingCredential -> "The previous connection is unchanged."
            state.errorCategory == EntryErrorCategory.CREDENTIAL_STORAGE_FAILED -> "Setup is not complete."
            else -> null
        }
    if (state.isSaving || state.isVerifying || standaloneError != null || completionLine != null) {
        Spacer(modifier = Modifier.height(16.dp))
        if (state.isSaving) {
            Text(
                text = "Gateway verified.\nSaving credential securely…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.isVerifying) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Verifying Gateway connection"
                        },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Verifying Gateway connection…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (standaloneError == EntryErrorCategory.CREDENTIAL_STORAGE_FAILED) {
            Text(
                text = "Gateway verified.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED.safeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        } else if (standaloneError != null) {
            Text(
                text = standaloneError.safeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        completionLine?.let { completion ->
            if (standaloneError != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = completion,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Spacer(modifier = Modifier.height(16.dp))
    }
    Button(
        onClick = {
            onEvent(
                if (state.errorCategory == null) {
                    EntryUiEvent.VerifyGatewayConnectionClicked
                } else {
                    EntryUiEvent.TryAgainClicked
                },
            )
        },
        enabled = !isPending,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
    ) {
        Text(text = if (state.errorCategory == null) state.actionLabel else "Try again")
    }
    if (state.isChangingCredential) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { onEvent(EntryUiEvent.CancelGatewayCredentialChangeClicked) },
            enabled = !isPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = "Cancel")
        }
    }
    if (state.isGatewayConnectionConfigured || state.isConnected) {
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = { onEvent(EntryUiEvent.RemoveGatewayConnectionClicked) },
            enabled = !isPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = "Remove Gateway Connection")
        }
    }
}

@Composable
private fun RemoveGatewayConnectionConfirmation(onEvent: (EntryUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked) },
        title = { Text(text = "Remove Gateway Connection?") },
        text = {
            Text(
                text =
                    "Remove the saved endpoint and credential from this device. " +
                        "Remote Sessions and Runs are not deleted or cancelled.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked) }) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked) }) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun ConnectedGatewayContent(onEvent: (EntryUiEvent) -> Unit) {
    Text(
        text = "Connected to Gateway",
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
        onClick = { onEvent(EntryUiEvent.ChangeGatewayCredentialClicked) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text = "Change Gateway credential")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onEvent(EntryUiEvent.RemoveGatewayConnectionClicked) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(text = "Remove Gateway Connection")
    }
}

package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun GatewayConnectionForm(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    var credentialVisible by remember(state.isChangingCredential) { mutableStateOf(false) }
    val isPending = state.isVerifying || state.isSaving
    GatewayEndpointField(state = state, isPending = isPending, onEvent = onEvent)
    Spacer(modifier = Modifier.height(12.dp))
    GatewayBearerCredentialField(
        state = state,
        isPending = isPending,
        onEvent = onEvent,
        credentialVisible = credentialVisible,
        onToggleVisibility = { credentialVisible = !credentialVisible },
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
    ConnectionStatusFeedback(state = state)
    VerifyConnectionButton(state = state, isPending = isPending, onEvent = onEvent)
    GatewayCredentialActions(state = state, isPending = isPending, onEvent = onEvent)
}

@Composable
private fun GatewayEndpointField(
    state: EntryUiState,
    isPending: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    if (state.isChangingCredential) {
        GatewayFixedEndpoint(endpoint = state.endpoint)
    } else {
        GatewayEditableEndpointField(state = state, isPending = isPending, onEvent = onEvent)
    }
}

@Composable
private fun GatewayFixedEndpoint(endpoint: String) {
    Text(
        text = "Gateway HTTPS endpoint",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = endpoint,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GatewayEditableEndpointField(
    state: EntryUiState,
    isPending: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
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

@Composable
private fun GatewayBearerCredentialField(
    state: EntryUiState,
    isPending: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
    credentialVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
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
                onClick = onToggleVisibility,
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
}

@Composable
private fun ConnectionStatusFeedback(state: EntryUiState) {
    val credentialError = state.errorCategory == EntryErrorCategory.AUTHENTICATION_FAILED
    val endpointSlotError = !state.isChangingCredential && state.errorCategory == EntryErrorCategory.INVALID_ADDRESS
    val standaloneError = state.errorCategory?.takeUnless { endpointSlotError || credentialError }
    val completionLine =
        when {
            state.errorCategory == null -> null
            state.isChangingCredential -> "The previous connection is unchanged."
            state.errorCategory == EntryErrorCategory.CREDENTIAL_STORAGE_FAILED -> "Setup is not complete."
            else -> null
        }
    val hasTerminalStatus = standaloneError != null || completionLine != null
    val showsStatus = state.isSaving || state.isVerifying || hasTerminalStatus
    if (showsStatus) {
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
        ConnectionFailureFeedback(standaloneError = standaloneError, completionLine = completionLine)
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ConnectionFailureFeedback(
    standaloneError: EntryErrorCategory?,
    completionLine: String?,
) {
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
}

@Composable
private fun VerifyConnectionButton(
    state: EntryUiState,
    isPending: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
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
}

@Composable
private fun GatewayCredentialActions(
    state: EntryUiState,
    isPending: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
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

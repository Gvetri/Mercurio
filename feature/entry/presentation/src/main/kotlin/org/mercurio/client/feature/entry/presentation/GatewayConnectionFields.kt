package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.unit.em

@Composable
internal fun GatewayAddressField(
    state: EntryUiState,
    focusRequester: FocusRequester,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val pending = state.isVerifying || state.isSaving
    val host = state.endpointHost()
    val invalid = host.isNotEmpty() && !host.isValidGatewayHost()
    val address = rememberEchoSafeTextFieldBuffer(host)
    OutlinedTextField(
        value = address.value,
        onValueChange = { change ->
            if (address.onUserChange(change)) {
                onEvent(EntryUiEvent.EndpointChanged(change.text.toGatewayEndpoint()))
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        enabled = !pending,
        isError = invalid,
        singleLine = false,
        maxLines = 2,
        shape = tokens.shapes.small,
        prefix = {
            Text(
                text = stringResource(R.string.gateway_address_prefix),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colorScheme.onSurfaceVariant,
            )
        },
        placeholder = {
            Text(
                text = stringResource(R.string.gateway_address_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colorScheme.onSurfaceVariant,
            )
        },
        label = {
            Text(
                text = stringResource(R.string.gateway_address_label),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
            )
        },
        supportingText = {
            Text(
                text = stringResource(R.string.gateway_address_support),
                style = MaterialTheme.typography.bodySmall,
            )
        },
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
internal fun GatewayCredentialField(
    state: EntryUiState,
    change: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val pending = state.isVerifying || state.isSaving
    var credentialVisible by remember(state.isChangingCredential) { mutableStateOf(false) }
    val rejected = state.errorCategory == EntryErrorCategory.AUTHENTICATION_FAILED
    val credential = rememberEchoSafeTextFieldBuffer(state.bearerCredential)
    OutlinedTextField(
        value = credential.value,
        onValueChange = { value ->
            if (credential.onUserChange(value)) {
                onEvent(EntryUiEvent.BearerCredentialChanged(value.text))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !pending,
        isError = rejected,
        singleLine = true,
        shape = tokens.shapes.small,
        visualTransformation = if (credentialVisible) VisualTransformation.None else PasswordVisualTransformation(),
        placeholder = { CredentialPlaceholder(change = change) },
        label = {
            Text(
                text = stringResource(R.string.gateway_credential_label),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
            )
        },
        trailingIcon = {
            if (state.bearerCredential.isNotEmpty() || credentialVisible) {
                CredentialTrailingAction(
                    visible = credentialVisible,
                    enabled = !pending,
                    onToggle = { credentialVisible = !credentialVisible },
                )
            }
        },
        supportingText = { CredentialSupportingText(rejected = rejected) },
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
private fun CredentialPlaceholder(change: Boolean) {
    Text(
        text =
            stringResource(
                if (change) R.string.gateway_credential_placeholder_new else R.string.gateway_credential_placeholder,
            ),
        style = MaterialTheme.typography.bodyLarge,
        color = LocalHermesDesignTokens.current.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CredentialSupportingText(rejected: Boolean) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = stringResource(if (rejected) R.string.gateway_error_credential else R.string.gateway_credential_support),
        style = MaterialTheme.typography.bodySmall,
        color = if (rejected) tokens.colorScheme.error else tokens.colorScheme.onSurfaceVariant,
        modifier =
            if (rejected) {
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            } else {
                Modifier
            },
    )
}

@Composable
private fun CredentialTrailingAction(
    visible: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val description =
        stringResource(
            if (visible) R.string.connection_credential_hide else R.string.connection_credential_show,
        )
    TextButton(
        onClick = onToggle,
        enabled = enabled,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(if (visible) R.string.gateway_bracket_hide else R.string.gateway_bracket_show),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun String.toGatewayEndpoint(): String {
    val host = removePrefix("https://").removePrefix("http://").trim().filterNot { it.isWhitespace() }
    return if (host.isEmpty()) "" else "https://$host"
}

internal fun EntryUiState.endpointHost(): String = endpoint.removePrefix("https://").removePrefix("http://")

internal fun String.isValidGatewayHost(): Boolean = isNotEmpty() && matches(GATEWAY_HOST_PATTERN)

private val GATEWAY_HOST_PATTERN = Regex("[A-Za-z0-9.\\-:_/]+")

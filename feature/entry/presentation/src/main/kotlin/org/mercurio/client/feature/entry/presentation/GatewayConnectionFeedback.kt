package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Composable
internal fun GatewayVerificationRow(state: EntryUiState) {
    val tokens = LocalHermesDesignTokens.current
    val failed = state.errorCategory != null && state.errorCategory != EntryErrorCategory.CREDENTIAL_STORAGE_FAILED
    val shown = state.isVerifying || state.isSaving || state.errorCategory != null
    if (!shown) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.s)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        VerificationHead(failed = failed, inFlight = state.isVerifying)
        if (!failed) {
            VerificationTrack(inFlight = state.isVerifying)
        }
        if (state.isVerifying) {
            Text(
                text = stringResource(R.string.gateway_verify_waiting),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun VerificationHead(
    failed: Boolean,
    inFlight: Boolean,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                stringResource(
                    when {
                        failed -> R.string.gateway_verify_not_verified
                        inFlight -> R.string.gateway_verify_checking
                        else -> R.string.gateway_verify_verified
                    },
                ),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = tokens.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        when {
            failed ->
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = tokens.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            inFlight ->
                Text(
                    text = stringResource(R.string.gateway_verify_in_progress),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
                    color = tokens.colorScheme.onSurfaceVariant,
                )
            else ->
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = tokens.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
        }
    }
}

@Composable
private fun VerificationTrack(inFlight: Boolean) {
    val tokens = LocalHermesDesignTokens.current
    LinearProgressIndicator(
        progress = { if (inFlight) GATEWAY_PROGRESS_IN_FLIGHT else 1f },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(tokens.shapes.extraLarge),
        color = tokens.colorScheme.primary,
        trackColor = tokens.colorScheme.surfaceContainerHighest,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
internal fun GatewayErrorBanner(
    state: EntryUiState,
    saved: Boolean,
    onChangeAddress: (() -> Unit)?,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val banner = state.gatewayBanner(saved, onChangeAddress, onEvent) ?: return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.small)
                .background(tokens.colorScheme.errorContainer)
                .padding(tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        Text(
            text = stringResource(banner.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colorScheme.onErrorContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        GatewayBracketedAction(
            label = banner.actionLabel,
            description = banner.actionDescription,
            color = tokens.colorScheme.onErrorContainer,
            onClick = banner.action,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

private data class GatewayBanner(
    val messageRes: Int,
    val actionLabel: String,
    val actionDescription: String,
    val action: () -> Unit,
)

@Composable
private fun EntryUiState.gatewayBanner(
    saved: Boolean,
    onChangeAddress: (() -> Unit)?,
    onEvent: (EntryUiEvent) -> Unit,
): GatewayBanner? {
    val tryAgainLabel = stringResource(R.string.gateway_bracket_try_again)
    val tryAgainDescription = stringResource(R.string.action_try_again)
    return when (errorCategory) {
        null, EntryErrorCategory.AUTHENTICATION_FAILED, EntryErrorCategory.INVALID_ADDRESS -> null
        EntryErrorCategory.SECURE_CONNECTION_FAILED ->
            GatewayBanner(
                messageRes = R.string.gateway_error_certificate,
                actionLabel = tryAgainLabel,
                actionDescription = tryAgainDescription,
                action = { onEvent(EntryUiEvent.TryAgainClicked) },
            )
        EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE ->
            GatewayBanner(
                messageRes = R.string.gateway_error_capability,
                actionLabel =
                    if (onChangeAddress == null) {
                        tryAgainLabel
                    } else {
                        stringResource(R.string.gateway_bracket_change_address)
                    },
                actionDescription =
                    if (onChangeAddress == null) {
                        tryAgainDescription
                    } else {
                        stringResource(R.string.gateway_change_address)
                    },
                action = onChangeAddress ?: { onEvent(EntryUiEvent.TryAgainClicked) },
            )
        EntryErrorCategory.CREDENTIAL_STORAGE_FAILED ->
            GatewayBanner(
                messageRes = R.string.gateway_error_storage,
                actionLabel = tryAgainLabel,
                actionDescription = tryAgainDescription,
                action = { onEvent(EntryUiEvent.TryAgainClicked) },
            )
        EntryErrorCategory.GATEWAY_REQUEST_FAILED ->
            GatewayBanner(
                messageRes = if (saved) R.string.gateway_error_reconnect_saved else R.string.gateway_error_network,
                actionLabel = tryAgainLabel,
                actionDescription = tryAgainDescription,
                action = { onEvent(EntryUiEvent.TryAgainClicked) },
            )
    }
}

@Composable
internal fun GatewayConnectionSummary(
    state: EntryUiState,
    onManage: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(tokens.spacing.xs))
        Text(
            text = state.endpoint,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colorScheme.onSurface,
        )
        Text(
            text = state.gatewayFacts?.resolve() ?: stringResource(R.string.gateway_facts_saved),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colorScheme.onSurfaceVariant,
        )
        GatewayBracketedAction(
            label = stringResource(R.string.gateway_bracket_manage),
            description = stringResource(R.string.gateway_manage_description),
            color = tokens.colorScheme.primary,
            onClick = onManage,
        )
    }
}

@Composable
internal fun GatewayFormActions(
    state: EntryUiState,
    change: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LocalHermesDesignTokens.current.spacing.s),
    ) {
        GatewayPrimaryAction(state = state, change = change, onEvent = onEvent)
        if (change) {
            OutlinedButton(
                onClick = { onEvent(EntryUiEvent.CancelGatewayCredentialChangeClicked) },
                enabled = !(state.isVerifying || state.isSaving),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = LocalHermesDesignTokens.current.shapes.small,
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun GatewayPrimaryAction(
    state: EntryUiState,
    change: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val pending = state.isVerifying || state.isSaving
    val enabled =
        when {
            pending -> false
            change -> state.bearerCredential.isNotBlank()
            else -> state.endpointHost().isValidGatewayHost() && state.bearerCredential.isNotBlank()
        }
    Button(
        onClick = { onEvent(EntryUiEvent.VerifyGatewayConnectionClicked) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = tokens.shapes.small,
        colors =
            if (state.isSaving) {
                ButtonDefaults.buttonColors(
                    disabledContainerColor = tokens.colorScheme.primary,
                    disabledContentColor = tokens.colorScheme.onPrimary,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tokens.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(tokens.spacing.s))
            Text(text = stringResource(R.string.gateway_action_saving))
        } else {
            Text(
                text =
                    stringResource(
                        if (change) R.string.gateway_action_verify_and_save else R.string.gateway_action_connect,
                    ),
            )
        }
    }
}

@Composable
internal fun GatewayBracketedAction(
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

internal fun EntryErrorCategory?.belongsToBanner(): Boolean =
    this != null &&
        this != EntryErrorCategory.AUTHENTICATION_FAILED &&
        this != EntryErrorCategory.INVALID_ADDRESS

private const val GATEWAY_PROGRESS_IN_FLIGHT = 0.38f

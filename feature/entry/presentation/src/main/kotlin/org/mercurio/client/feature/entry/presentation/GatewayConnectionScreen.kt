package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Composable
internal fun GatewayConnectionScreen(
    state: EntryUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    val mode = state.gatewayScreenMode()
    val addressFocus = remember { FocusRequester() }
    var menuOpen by remember(state.isChangingCredential, state.isConnected) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        GatewayTopBar(
            state = state,
            saved = mode != GatewayScreenMode.ADD,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onEvent = onEvent,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(tokens.shapes.large)
                    .background(tokens.colorScheme.surface),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(spacing.m),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
                        state.inboundLinkNotice?.let { notice ->
                            InboundLinkNoticeText(notice = notice)
                        }
                        GatewayBodyContent(
                            state = state,
                            mode = mode,
                            addressFocus = addressFocus,
                            onManage = { menuOpen = true },
                            onEvent = onEvent,
                        )
                    }
                    if (mode != GatewayScreenMode.SAVED && !state.errorCategory.belongsToBanner()) {
                        GatewayFormActions(
                            state = state,
                            change = mode == GatewayScreenMode.CHANGE,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

private enum class GatewayScreenMode { ADD, CHANGE, SAVED }

private fun EntryUiState.gatewayScreenMode(): GatewayScreenMode =
    when {
        isChangingCredential -> GatewayScreenMode.CHANGE
        isConnected -> GatewayScreenMode.SAVED
        isGatewayConnectionConfigured && errorCategory.belongsToBanner() -> GatewayScreenMode.SAVED
        else -> GatewayScreenMode.ADD
    }

@Composable
private fun GatewayTopBar(
    state: EntryUiState,
    saved: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    val backDescription = stringResource(R.string.gateway_back)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.xxxl)
                .clip(tokens.shapes.large)
                .background(tokens.colorScheme.surface)
                .padding(horizontal = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(spacing.xxxl)
                    .clip(tokens.shapes.small)
                    .clickable(role = Role.Button, onClick = { onEvent(gatewayBackEvent(state)) })
                    .semantics { contentDescription = backDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_back),
                contentDescription = null,
                tint = tokens.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(if (saved) R.string.gateway_bar_title else R.string.gateway_bar_add),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.06.em),
            color = tokens.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = spacing.s).semantics { heading() },
        )
        if (state.isGatewayConnectionConfigured || state.isConnected) {
            GatewayActionsMenu(
                mark = stringResource(R.string.gateway_actions_mark),
                menuOpen = menuOpen,
                onMenuOpenChange = onMenuOpenChange,
                onEvent = onEvent,
            )
        }
    }
}

private fun gatewayBackEvent(state: EntryUiState): EntryUiEvent =
    when {
        state.isChangingCredential -> EntryUiEvent.CancelGatewayCredentialChangeClicked
        state.isConnected && state.sessionList != null -> EntryUiEvent.ReturnToSessionListClicked
        else -> EntryUiEvent.CloseGatewayConnectionSetupClicked
    }

@Composable
private fun GatewayActionsMenu(
    mark: String,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val actionsDescription = stringResource(R.string.gateway_actions)
    Box {
        Box(
            modifier =
                Modifier
                    .widthIn(min = tokens.spacing.xxxl)
                    .heightIn(min = tokens.spacing.xxxl)
                    .clip(tokens.shapes.small)
                    .background(tokens.colorScheme.surfaceContainerLow)
                    .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.small)
                    .clickable(role = Role.Button) { onMenuOpenChange(true) }
                    .semantics { contentDescription = actionsDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.14.em),
                color = tokens.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.settings_action_change_credential)) },
                onClick = {
                    onMenuOpenChange(false)
                    onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.settings_action_remove_connection),
                        color = tokens.colorScheme.error,
                    )
                },
                onClick = {
                    onMenuOpenChange(false)
                    onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
                },
            )
        }
    }
}

@Composable
private fun GatewayBodyContent(
    state: EntryUiState,
    mode: GatewayScreenMode,
    addressFocus: FocusRequester,
    onManage: () -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    when (mode) {
        GatewayScreenMode.CHANGE -> GatewayFormContent(state, true, addressFocus, onManage, onEvent)
        GatewayScreenMode.SAVED -> SavedConnectionContent(state, onManage, onEvent)
        GatewayScreenMode.ADD -> GatewayFormContent(state, false, addressFocus, onManage, onEvent)
    }
}

@Composable
private fun GatewayFormContent(
    state: EntryUiState,
    change: Boolean,
    addressFocus: FocusRequester,
    onManage: () -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    GatewayFormHead(change = change)
    GatewayErrorBanner(
        state = state,
        saved = false,
        onChangeAddress = if (change) null else ({ addressFocus.requestFocus() }),
        onEvent = onEvent,
    )
    GatewayVerificationRow(state = state)
    if (change) {
        GatewayConnectionSummary(state = state, onManage = onManage)
    } else {
        GatewayAddressField(state = state, focusRequester = addressFocus, onEvent = onEvent)
    }
    GatewayCredentialField(state = state, change = change, onEvent = onEvent)
}

@Composable
private fun SavedConnectionContent(
    state: EntryUiState,
    onManage: () -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    GatewayFormHead(change = false, reconnectFailed = !state.isConnected, saved = state.isConnected)
    GatewayErrorBanner(state = state, saved = true, onChangeAddress = null, onEvent = onEvent)
    GatewayConnectionSummary(state = state, onManage = onManage)
}

@Composable
private fun GatewayFormHead(
    change: Boolean,
    reconnectFailed: Boolean = false,
    saved: Boolean = false,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(
            text =
                stringResource(
                    if (change || reconnectFailed || saved) {
                        R.string.gateway_saved_title
                    } else {
                        R.string.gateway_form_title
                    },
                ),
            style = MaterialTheme.typography.titleLarge,
            color = tokens.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text =
                stringResource(
                    when {
                        change -> R.string.gateway_saved_lede_change
                        reconnectFailed -> R.string.gateway_saved_lede_reconnect_failed
                        saved -> R.string.gateway_saved_lede
                        else -> R.string.gateway_form_lede
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colorScheme.onSurfaceVariant,
        )
    }
}

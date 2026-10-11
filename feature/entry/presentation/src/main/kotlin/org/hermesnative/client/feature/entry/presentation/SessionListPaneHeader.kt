package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionListPaneHeader(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    var removeGatewayConfirmationOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SessionPaneTitleRow(
            state = state,
            onEvent = onEvent,
            onRemoveGatewayConnectionRequested = { removeGatewayConfirmationOpen = true },
        )
        Spacer(modifier = Modifier.height(8.dp))

        SessionSearchControls(
            state = state,
            onEvent = onEvent,
            onSearchFocusChanged = onSearchFocusChanged,
        )
        Spacer(modifier = Modifier.height(12.dp))

        SessionPaneActionRow(state = state, onEvent = onEvent)
        if (state.showFirstUseGuidance) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Select a Session to open its Gateway history. Refresh to load the latest server state.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (removeGatewayConfirmationOpen) {
        RemoveGatewayConnectionConfirmation(
            onConfirm = {
                removeGatewayConfirmationOpen = false
                onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
            },
            onDismiss = { removeGatewayConfirmationOpen = false },
        )
    }
}

@Composable
private fun SessionPaneTitleRow(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    onRemoveGatewayConnectionRequested: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier =
                    Modifier.semantics {
                        contentDescription = "More options"
                    },
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = "Change credential") },
                    enabled = state.createSession == null,
                    onClick = {
                        menuExpanded = false
                        onEvent(EntryUiEvent.ChangeGatewayCredentialClicked)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "Local diagnostics") },
                    enabled = state.createSession == null && !state.hasPendingMutation,
                    onClick = {
                        menuExpanded = false
                        onEvent(EntryUiEvent.OpenLocalDiagnosticsClicked)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(text = "Remove Gateway Connection") },
                    enabled = state.createSession == null,
                    onClick = {
                        menuExpanded = false
                        onRemoveGatewayConnectionRequested()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionPaneActionRow(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onEvent(EntryUiEvent.CreateSessionClicked) },
            enabled = state.createSessionActionEnabled(),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(text = "Create Session")
        }
        TextButton(
            onClick = { onEvent(EntryUiEvent.RefreshSessionListClicked) },
            enabled = state.refreshListActionEnabled(),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(text = if (state.isUnavailable) "Try again" else "Refresh")
        }
    }
}

private fun SessionListUiState.createSessionActionEnabled(): Boolean =
    !isLoading &&
        !isSearching &&
        !isRefreshing &&
        !isLoadingMore &&
        !isUnavailable &&
        openingSessionId == null &&
        sessionMutations.isEmpty() &&
        createSession == null

private fun SessionListUiState.refreshListActionEnabled(): Boolean =
    !isLoading &&
        !isRefreshing &&
        openingSessionId == null &&
        !hasPendingMutation &&
        createSession == null &&
        openedSession?.isRefreshing != true &&
        openedSession?.isReconciliationInProgress != true

@Composable
private fun SessionSearchControls(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { onEvent(EntryUiEvent.SessionSearchQueryChanged(it)) },
        enabled = state.sessionMutations.isEmpty(),
        label = { Text(text = "Search Sessions") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { onSearchFocusChanged(it.isFocused) },
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Search loaded titles and previews.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
    if (state.searchQuery.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onEvent(EntryUiEvent.ClearSessionSearchClicked) },
            enabled = state.sessionMutations.isEmpty(),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(text = "Clear search")
        }
    }
}

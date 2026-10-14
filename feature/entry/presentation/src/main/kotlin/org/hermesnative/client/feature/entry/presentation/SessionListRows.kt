package org.hermesnative.client.feature.entry.presentation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SessionRow(
    row: SessionRowUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val mutation = row.mutation
    val inlineMode = mutation?.rename != null
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = row.enabled && mutation?.pendingAction == null,
                        onClick = row.onClick,
                    )
                    .semantics { this.selected = row.selected },
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = row.session.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                row.session.preview?.let { preview ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SessionRowStateCues(selected = row.selected, pinned = row.session.pinned)
            }
            if (!inlineMode) {
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    SessionActionMenu(
                        session = row.session,
                        enabled = row.enabled && mutation?.pendingAction == null,
                        onEvent = onEvent,
                    )
                }
            }
        }
        SessionRowMutationArea(row = row, onEvent = onEvent)
    }
}

@Composable
private fun SessionRowMutationArea(
    row: SessionRowUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val mutation = row.mutation
    if (mutation?.delete != null) return
    when {
        mutation?.rename != null ->
            RenameSessionContent(
                session = row.session,
                state = mutation.rename,
                enabled = row.enabled,
                onEvent = onEvent,
            )
        else ->
            SessionMutationRowFeedback(
                session = row.session,
                mutation = mutation,
                enabled = row.enabled,
                onEvent = onEvent,
            )
    }
}

@Composable
private fun SessionRowStateCues(
    selected: Boolean,
    pinned: Boolean,
) {
    if (!selected && !pinned) return
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected) {
            Text(
                text = "Open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pinned) {
            Text(
                text = "Pinned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionMutationRowFeedback(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        SessionMutationFeedback(
            session = session,
            mutation = mutation,
            enabled = enabled,
            onEvent = onEvent,
        )
    }
}

@Composable
internal fun SessionActionControls(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
    showMenu: Boolean = true,
) {
    if (mutation?.delete != null) return
    when {
        mutation?.rename != null ->
            RenameSessionContent(
                session = session,
                state = mutation.rename,
                enabled = enabled,
                onEvent = onEvent,
            )
        else -> {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (showMenu) {
                    SessionActionMenu(
                        session = session,
                        enabled = enabled && mutation?.pendingAction == null,
                        onEvent = onEvent,
                    )
                }
                SessionMutationFeedback(
                    session = session,
                    mutation = mutation,
                    enabled = enabled,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun SessionMutationFeedback(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    mutation?.pendingAction?.let { action ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = action.pendingMessage(),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    mutation?.errorCategory?.let { category ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = category.safeMessage,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    SessionMutationRetryControl(
        session = session,
        mutation = mutation,
        enabled = enabled,
        onEvent = onEvent,
    )
}

@Composable
private fun SessionMutationRetryControl(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val retryEvent =
        when (mutation?.retryAction) {
            SessionMutationAction.PIN -> EntryUiEvent.PinSessionClicked(session.id)
            SessionMutationAction.UNPIN -> EntryUiEvent.UnpinSessionClicked(session.id)
            SessionMutationAction.RENAME, SessionMutationAction.DELETE, null -> null
        }
    retryEvent?.let { event ->
        OutlinedButton(
            onClick = { onEvent(event) },
            enabled = enabled && mutation?.pendingAction == null,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(text = "Try again")
        }
    }
}

private fun SessionMutationAction.pendingMessage(): String =
    when (this) {
        SessionMutationAction.RENAME -> "Renaming Session…"
        SessionMutationAction.PIN -> "Pinning Session…"
        SessionMutationAction.UNPIN -> "Unpinning Session…"
        SessionMutationAction.DELETE -> "Deleting Session…"
    }

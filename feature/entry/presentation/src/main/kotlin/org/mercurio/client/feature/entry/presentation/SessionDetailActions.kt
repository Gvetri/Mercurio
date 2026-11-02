package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The Session detail surface keeps the inline rename editor and the compact
 * mutation feedback. The Session list itself reads rows from [SessionRow].
 */
@Composable
internal fun SessionActionControls(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
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
        else ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                SessionMutationFeedback(
                    session = session,
                    mutation = mutation,
                    enabled = enabled,
                    onEvent = onEvent,
                )
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
            text = stringResource(action.pendingLabelRes()),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    mutation?.errorCategory?.let { category ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(category.messageRes),
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
            Text(text = stringResource(R.string.action_try_again))
        }
    }
}

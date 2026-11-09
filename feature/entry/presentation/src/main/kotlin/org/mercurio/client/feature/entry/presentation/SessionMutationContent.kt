package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun RenameSessionContent(
    session: SessionItemUiState,
    state: SessionRenameUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.sessions_rename),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = stringResource(R.string.sessions_current_title, sessionTitle(session)))
        Spacer(modifier = Modifier.height(8.dp))
        val title = rememberEchoSafeTextFieldBuffer(state.titleDraft, session.id)
        OutlinedTextField(
            value = title.value,
            onValueChange = { change ->
                if (title.onUserChange(change)) onEvent(EntryUiEvent.RenameSessionTitleChanged(session.id, change.text))
            },
            enabled = enabled && !state.isSubmitting,
            label = { Text(stringResource(R.string.sessions_new_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) },
            enabled = enabled && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                text =
                    if (state.errorCategory == SessionRenameErrorCategory.GATEWAY_REQUEST_FAILED) {
                        stringResource(R.string.action_try_again)
                    } else {
                        stringResource(R.string.action_confirm_rename)
                    },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = { onEvent(EntryUiEvent.CancelRenameSessionClicked(session.id)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = stringResource(R.string.action_cancel_rename))
        }
        SessionMutationProgress(
            visible = state.isSubmitting,
            label = stringResource(R.string.sessions_renaming),
            topSpacing = 8.dp,
        )
        SessionMutationErrorMessage(message = state.errorCategory?.messageRes, topSpacing = 8.dp)
    }
}

@Composable
internal fun SessionMutationProgress(
    visible: Boolean,
    label: String,
    topSpacing: Dp,
) {
    if (!visible) return
    Spacer(modifier = Modifier.height(topSpacing))
    CircularProgressIndicator(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        text = label,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun SessionMutationErrorMessage(
    message: Int?,
    topSpacing: Dp,
) {
    if (message == null) return
    Spacer(modifier = Modifier.height(topSpacing))
    Text(
        text = stringResource(message),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

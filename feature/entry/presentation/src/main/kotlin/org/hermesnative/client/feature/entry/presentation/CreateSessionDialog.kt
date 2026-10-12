package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * The approved standard Material 3 Create Session dialog: one optional title, the
 * Untitled Session fallback, and the exact Create/Cancel actions. The dialog owns
 * nothing but presentation; the in-memory draft and the guarded confirmation event
 * stay in the state holder, so no dismissal path can create a Session.
 */
@Composable
internal fun CreateSessionDialog(
    state: SessionCreationUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelCreateSessionClicked) },
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = {
            Text(
                text = "Create Session",
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = state.titleDraft,
                    onValueChange = { onEvent(EntryUiEvent.CreateSessionTitleChanged(it)) },
                    enabled = !state.isSubmitting,
                    label = { Text(text = "Title (optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                CreateSessionDialogStatus(state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.ConfirmCreateSessionClicked) },
                enabled = !state.isSubmitting,
            ) {
                Text(text = if (state.errorCategory == null) "Create" else "Try again")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.CancelCreateSessionClicked) },
                enabled = !state.isSubmitting,
            ) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun CreateSessionDialogStatus(state: SessionCreationUiState) {
    when {
        state.isSubmitting ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = "Creating Session…",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        state.errorCategory != null ->
            Text(
                text = state.errorCategory.safeMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        else -> Text(text = "Leave blank for Untitled Session.")
    }
}

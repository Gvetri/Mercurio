package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The failed-send warning confirmation: dismissal or Back keeps the unresolved-send marker,
 * and only an explicit confirm clears this device's warning. The composer text is never
 * removed by this dialog.
 */
@Composable
internal fun UnresolvedSendDiscardConfirmation(onEvent: (EntryUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelDiscardUnresolvedSendClicked) },
        title = { Text(text = stringResource(R.string.dialog_discard_unsent_title)) },
        text = {
            Text(text = stringResource(R.string.dialog_discard_unsent_body))
        },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.ConfirmDiscardUnresolvedSendClicked) }) {
                Text(text = stringResource(R.string.action_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.CancelDiscardUnresolvedSendClicked) }) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

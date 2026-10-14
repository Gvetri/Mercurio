package org.hermesnative.client.feature.entry.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The failed-send discard confirmation: dismissal or Back keeps the unresolved
 * submission record, and only an explicit confirm removes this client's record.
 * The composer text is never removed by this dialog.
 */
@Composable
internal fun UnresolvedSendDiscardConfirmation(onEvent: (EntryUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelDiscardUnresolvedSendClicked) },
        title = { Text(text = "Discard the unsent message record?") },
        text = {
            Text(
                text =
                    "Discarding clears this client's record of the failed send. " +
                        "The Gateway may still have received the message, so sending the " +
                        "draft again could create a duplicate. Your draft stays in the composer.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.ConfirmDiscardUnresolvedSendClicked) }) {
                Text(text = "Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.CancelDiscardUnresolvedSendClicked) }) {
                Text(text = "Cancel")
            }
        },
    )
}

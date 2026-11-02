package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * App-modal delete confirmation for a session, hosted once at the entry root and mirroring the
 * Remove-Gateway-Connection dialog: the pending action is named, deletion is warned about as
 * irreversible, and Cancel + Confirm are always reachable together. Dismissal or Back keeps the
 * session; only an explicit confirm invokes the existing delete action.
 */
@Composable
internal fun DeleteSessionConfirmation(
    pending: PendingSessionDelete,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val delete = pending.mutation.delete ?: return
    val title = sessionTitle(pending.session)
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelDeleteSessionClicked(pending.session.id)) },
        title = { Text(text = stringResource(R.string.dialog_delete_session_title, title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.dialog_delete_session_body))
                SessionMutationProgress(
                    visible = delete.isSubmitting,
                    label = stringResource(R.string.sessions_deleting),
                    topSpacing = 4.dp,
                )
                SessionMutationErrorMessage(
                    message = pending.mutation.errorCategory?.messageRes,
                    topSpacing = 4.dp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.ConfirmDeleteSessionClicked(pending.session.id)) },
                enabled = pending.allowsMutation && !delete.isSubmitting,
            ) {
                Text(
                    text =
                        if (pending.mutation.errorCategory == null) {
                            stringResource(R.string.action_delete)
                        } else {
                            stringResource(R.string.action_try_again)
                        },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.CancelDeleteSessionClicked(pending.session.id)) },
                enabled = !delete.isSubmitting,
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        modifier = modifier,
    )
}

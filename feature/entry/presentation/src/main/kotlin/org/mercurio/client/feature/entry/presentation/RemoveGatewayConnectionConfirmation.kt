package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The D-02 Gateway-removal confirmation contract: dismissal or Back leaves the stored
 * connection untouched, and only an explicit confirm invokes the existing local removal
 * action once. No network request, progress stage or fabricated result is added.
 */
@Composable
internal fun RemoveGatewayConnectionConfirmation(onEvent: (EntryUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked) },
        title = { Text(text = stringResource(R.string.dialog_remove_connection_title)) },
        text = {
            Text(text = stringResource(R.string.dialog_remove_connection_body))
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(EntryUiEvent.CancelRemoveGatewayConnectionClicked) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

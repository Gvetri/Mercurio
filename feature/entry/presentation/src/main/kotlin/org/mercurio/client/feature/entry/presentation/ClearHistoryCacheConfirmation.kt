package org.mercurio.client.feature.entry.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The Settings cache-clear confirmation contract: dismissal or Back leaves the store
 * untouched, and only an explicit confirm invokes the local clear once. The explanation
 * distinguishes the cached conversations this action removes from the remote Sessions
 * and Runs and the already-exported diagnostic files it never touches.
 */
@Composable
internal fun ClearHistoryCacheConfirmation(onEvent: (EntryUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryUiEvent.CancelClearHistoryCacheClicked) },
        title = { Text(text = stringResource(R.string.dialog_clear_history_cache_title)) },
        text = {
            Text(text = stringResource(R.string.dialog_clear_history_cache_body))
        },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.ConfirmClearHistoryCacheClicked) }) {
                Text(text = stringResource(R.string.action_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryUiEvent.CancelClearHistoryCacheClicked) }) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

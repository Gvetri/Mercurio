package org.hermesnative.client.feature.entry.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The D-02 Gateway-removal confirmation contract: dismissal or Back leaves the stored
 * connection untouched, and only an explicit confirm invokes the existing local removal
 * action once. No network request, progress stage or fabricated result is added.
 */
@Composable
internal fun RemoveGatewayConnectionConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove Gateway Connection?") },
        text = {
            Text(
                text =
                    "Remove the saved endpoint and credential from this device. " +
                        "Remote Sessions and Runs are not deleted or cancelled.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * The actions a message surface offers. Every action runs only from an explicit user
 * selection, which is why they are plain callbacks: the renderer never invokes one by
 * itself, and tests can record exactly what a selection carried out of the client.
 */
internal data class MessageActions(
    val openLink: (String) -> Unit,
    val copy: (String) -> Unit,
    val share: (String) -> Unit,
)

/** The platform actions: clipboard copy, the Android Share chooser, and the external browser. */
@Composable
internal fun rememberMessageActions(): MessageActions {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        MessageActions(
            openLink = { destination ->
                externalBrowserIntent(destination)?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            },
            copy = { text -> clipboard.setText(AnnotatedString(text)) },
            share = { text ->
                runCatching { context.startActivity(messageShareIntent(text)) }
            },
        )
    }
}

/**
 * The visible per-message three-dot menu. [payload] is the message's exact raw content when
 * it is eligible to leave the client, and null otherwise: an ineligible message keeps the
 * visible trigger while both items stay disabled and run no callback. Opening or dismissing
 * the menu never runs an action on its own.
 */
@Composable
internal fun MessageActionMenu(
    messageId: String,
    payload: String?,
    actions: MessageActions,
) {
    var expanded by remember(messageId) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier =
                Modifier.size(48.dp).semantics {
                    contentDescription = "Message actions for $messageId"
                },
        ) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Copy message") },
                onClick = {
                    expanded = false
                    payload?.let(actions.copy)
                },
                enabled = payload != null,
            )
            DropdownMenuItem(
                text = { Text("Share message") },
                onClick = {
                    expanded = false
                    payload?.let(actions.share)
                },
                enabled = payload != null,
            )
        }
    }
}

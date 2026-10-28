package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SessionActionMenu(
    session: SessionItemUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    var expanded by remember(session.id, enabled) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier =
                Modifier.size(48.dp).testTag("sessions-row-actions").semantics {
                    testTagsAsResourceId = true
                    contentDescription = "Session actions for ${session.title}"
                },
        ) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (session.pinned) "Unpin Session" else "Pin Session") },
                onClick = {
                    expanded = false
                    onEvent(
                        if (session.pinned) {
                            EntryUiEvent.UnpinSessionClicked(session.id)
                        } else {
                            EntryUiEvent.PinSessionClicked(session.id)
                        },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("Rename Session") },
                onClick = {
                    expanded = false
                    onEvent(EntryUiEvent.RenameSessionClicked(session.id))
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete Session") },
                onClick = {
                    expanded = false
                    onEvent(EntryUiEvent.DeleteSessionClicked(session.id))
                },
            )
        }
    }
}

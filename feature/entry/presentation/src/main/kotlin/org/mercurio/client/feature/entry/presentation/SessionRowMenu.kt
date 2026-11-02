package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SessionRowMenu(
    expanded: Boolean,
    row: SessionRowUiState,
    onDismiss: () -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val mutation = row.mutation
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = tokens.shapes.extraSmall,
        containerColor = tokens.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = tokens.elevation.level2,
        modifier = Modifier.width(SESSION_MENU_WIDTH),
    ) {
        SessionMenuItem(
            label =
                stringResource(
                    if (row.session.pinned) R.string.sessions_unpin_short else R.string.sessions_pin_short,
                ),
            enabled = row.enabled && mutation?.pendingAction == null,
        ) {
            onDismiss()
            onEvent(
                if (row.session.pinned) {
                    EntryUiEvent.UnpinSessionClicked(row.session.id)
                } else {
                    EntryUiEvent.PinSessionClicked(row.session.id)
                },
            )
        }
        SessionMenuItem(
            label = stringResource(R.string.sessions_rename_short),
            enabled = row.enabled && mutation?.pendingAction == null,
        ) {
            onDismiss()
            onEvent(EntryUiEvent.RenameSessionClicked(row.session.id))
        }
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        SessionMenuItem(
            label = stringResource(R.string.sessions_delete_short),
            enabled = row.enabled && mutation?.pendingAction == null,
            destructive = true,
        ) {
            onDismiss()
            onEvent(EntryUiEvent.DeleteSessionClicked(row.session.id))
        }
    }
}

@Composable
internal fun SessionMenuItem(
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    when {
                        !enabled -> tokens.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
                        destructive -> tokens.colorScheme.error
                        else -> tokens.colorScheme.onSurface
                    },
            )
        },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = SESSION_MENU_ITEM_HEIGHT),
    )
}

private val SESSION_MENU_WIDTH = 216.dp
private val SESSION_MENU_ITEM_HEIGHT = 44.dp
private const val DISABLED_CONTENT_ALPHA = 0.38f

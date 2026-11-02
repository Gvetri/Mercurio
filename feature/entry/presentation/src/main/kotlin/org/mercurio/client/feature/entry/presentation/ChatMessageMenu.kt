package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.padding
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
import org.mercurio.client.feature.entry.domain.RunId
import java.time.Instant

/**
 * The per-Message action menu: Copy message, Copy run ID and Share, with the run and the full
 * timestamp in the menu's last line. An ineligible Message keeps the visible trigger while the
 * items that would export it stay disabled.
 */
@Composable
internal fun ChatMessageMenu(
    row: ChatRow,
    actions: MessageActions,
    onDismiss: () -> Unit,
) {
    val meta = chatMenuMeta(runId = row.runId, timestamp = row.timestamp)
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(CHAT_MENU_WIDTH),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_copy_message)) },
            onClick = {
                onDismiss()
                row.payload?.let(actions.copy)
            },
            enabled = row.payload != null,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_copy_run_id)) },
            onClick = {
                onDismiss()
                row.runId?.let { id -> actions.copy(id.value) }
            },
            enabled = row.runId != null,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_share)) },
            onClick = {
                onDismiss()
                row.payload?.let(actions.share)
            },
            enabled = row.payload != null,
        )
        meta?.let { metaText ->
            HorizontalDivider()
            Text(
                text = metaText.resolve(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            )
        }
    }
}

/** The menu's dim last line: the short run, the full date and the exact time. */
internal fun chatMenuMeta(
    runId: RunId?,
    timestamp: Instant?,
): UiText? {
    val parts =
        listOfNotNull(
            runId?.let(::chatRunLabel),
            timestamp?.let { instant -> UiText.Value(chatMenuTimestamp(instant)) },
            timestamp?.let { instant -> UiText.Value(chatMenuTime(instant)) },
        )
    return if (parts.isEmpty()) null else UiText.Joined(parts = parts, separator = CHAT_META_SEPARATOR)
}

private const val CHAT_META_SEPARATOR = " · "

private val CHAT_MENU_WIDTH = 216.dp

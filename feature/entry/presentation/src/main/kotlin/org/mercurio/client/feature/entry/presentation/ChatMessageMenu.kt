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
            text = { Text("Copy message") },
            onClick = {
                onDismiss()
                row.payload?.let(actions.copy)
            },
            enabled = row.payload != null,
        )
        DropdownMenuItem(
            text = { Text("Copy run ID") },
            onClick = {
                onDismiss()
                row.runId?.let { id -> actions.copy(id.value) }
            },
            enabled = row.runId != null,
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = {
                onDismiss()
                row.payload?.let(actions.share)
            },
            enabled = row.payload != null,
        )
        if (meta.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = meta,
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
): String =
    listOfNotNull(
        runId?.let(::chatRunLabel),
        timestamp?.let { instant -> chatMenuTimestamp(instant) },
        timestamp?.let { instant -> chatMenuTime(instant) },
    ).joinToString(separator = " · ")

private val CHAT_MENU_WIDTH = 216.dp

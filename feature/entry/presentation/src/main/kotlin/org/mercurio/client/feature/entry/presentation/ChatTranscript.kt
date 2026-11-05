package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * The transcript region: the day separators and Message rows the Session holds, pinned to the
 * newest content the way a reader expects an opened conversation to open. The run line and the
 * composer stay outside the scroll region, so the composer never moves. [sessionKey] makes a
 * different Session start from its own state, even when a Message id repeats.
 */
@Composable
internal fun ChatTranscript(
    items: List<ChatTranscriptItem>,
    listState: LazyListState,
    callbacks: ChatCallbacks,
    sessionKey: Any,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.m,
                    end = tokens.spacing.m,
                    top = tokens.spacing.xs,
                    bottom = tokens.spacing.xs,
                ),
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Bottom),
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is ChatTranscriptItem.Day -> "day-${item.label}"
                    is ChatTranscriptItem.Message -> item.row.id
                }
            },
            contentType = { item ->
                when (item) {
                    is ChatTranscriptItem.Day -> CHAT_DAY_CONTENT_TYPE
                    is ChatTranscriptItem.Message -> CHAT_MESSAGE_CONTENT_TYPE
                }
            },
        ) { item ->
            key(sessionKey) {
                when (item) {
                    is ChatTranscriptItem.Day -> ChatDaySeparator(label = item.label)
                    is ChatTranscriptItem.Message ->
                        ChatMessageRow(row = item.row, callbacks = callbacks)
                }
            }
        }
    }
}

private const val CHAT_DAY_CONTENT_TYPE = "chat-day"

private const val CHAT_MESSAGE_CONTENT_TYPE = "chat-message"

/** The upper-case day label between two rules, one per day the transcript crosses. */
@Composable
internal fun ChatDaySeparator(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.xs, bottom = tokens.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
    ) {
        DayRule()
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.16.em,
            color = tokens.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        DayRule()
    }
}

@Composable
private fun RowScope.DayRule() {
    val tokens = LocalHermesDesignTokens.current
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        thickness = 1.dp,
        color = tokens.colorScheme.outlineVariant,
    )
}

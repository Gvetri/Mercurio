package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One Message body: the Markdown blocks the shell renders, folded after twelve rendered lines the
 * way the approved design draws a long Message. The fold discloses itself only when the body
 * passes the limit, and it announces its state through the control's label. A row the Run is still
 * writing never folds: its newest line must stay on screen.
 */
@Composable
internal fun ChatMessageBody(
    row: ChatRow,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(row.id) { mutableStateOf(false) }
    val foldable = row.body.foldsPastTwelveLines()
    val folded = foldable && !expanded && !row.streaming
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (folded) CHAT_FOLD_HEIGHT else Dp.Infinity)
                    .clipToBounds(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                row.body?.let { body ->
                    MarkdownContent(
                        markdown = body,
                        actions = actions,
                        blockSpacing = LocalHermesDesignTokens.current.spacing.xs,
                    )
                }
                if (row.streaming) ChatStreamingCaret(modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (foldable && !row.streaming) {
            ChatTextControl(
                label = if (expanded) "[ show less ]" else "[ show more ]",
                enabled = true,
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("chat-fold-${row.id}"),
            )
        }
    }
}

/** Twelve rendered lines of the transcript body, the height the design clips a long Message at. */
internal val CHAT_FOLD_HEIGHT = 240.dp

private const val CHAT_FOLD_LINES = 12

private const val CHAT_FOLD_LINE_CHARS = 44

private fun String?.foldsPastTwelveLines(): Boolean {
    if (this == null) return false
    val lines = lines().size
    return lines > CHAT_FOLD_LINES || length > CHAT_FOLD_LINES * CHAT_FOLD_LINE_CHARS
}

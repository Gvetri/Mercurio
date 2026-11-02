package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * One transcript Message in the approved Chat design. The role decides the variant: an assistant
 * row carries no container, the reader's row is a bordered bubble, a system row is a quiet inset,
 * and a failed turn is its own error-container row with the safe message and its two actions.
 */
@Composable
internal fun ChatMessageRow(
    row: ChatRow,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    when (row.kind) {
        ChatRowKind.User -> ChatUserRow(row = row, callbacks = callbacks, pending = false, modifier = modifier)
        ChatRowKind.Pending -> ChatUserRow(row = row, callbacks = callbacks, pending = true, modifier = modifier)
        ChatRowKind.Failed -> ChatFailedRow(row = row, callbacks = callbacks, modifier = modifier)
        ChatRowKind.System -> ChatSystemRow(row = row, callbacks = callbacks, modifier = modifier)
        ChatRowKind.Assistant -> ChatAssistantRow(row = row, callbacks = callbacks, modifier = modifier)
    }
}

@Composable
private fun ChatAssistantRow(
    row: ChatRow,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = tokens.spacing.s)) {
        ChatMessageHeader(
            row = row,
            colors = ChatHeaderColors(label = tokens.colorScheme.primary, time = tokens.colorScheme.onSurfaceVariant),
            callbacks = callbacks,
            modifier = Modifier.padding(bottom = tokens.spacing.xs),
        )
        ChatMessageBody(row = row, callbacks = callbacks)
    }
}

@Composable
private fun ChatUserRow(
    row: ChatRow,
    callbacks: ChatCallbacks,
    pending: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val bubbleShape = userBubbleShape()
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(vertical = tokens.spacing.s)) {
        val bubbleWidth = maxWidth * USER_BUBBLE_FRACTION
        Surface(
            modifier =
                Modifier
                    .widthIn(max = bubbleWidth)
                    .align(Alignment.CenterEnd)
                    .then(
                        if (pending) {
                            Modifier.chatDashedBorder(color = tokens.colorScheme.outlineVariant, shape = bubbleShape)
                        } else {
                            Modifier
                        },
                    ),
            color = tokens.componentColors.inset,
            shape = bubbleShape,
            border = if (pending) null else BorderStroke(1.dp, tokens.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.s)) {
                ChatMessageHeader(
                    row = row,
                    colors =
                        ChatHeaderColors(
                            label = tokens.syntaxColors.string,
                            time = tokens.colorScheme.onSurfaceVariant,
                        ),
                    callbacks = callbacks,
                    pending = pending,
                    modifier = Modifier.padding(bottom = tokens.spacing.xs),
                )
                ChatMessageBody(row = row, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun ChatSystemRow(
    row: ChatRow,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = tokens.spacing.s),
        color = tokens.componentColors.inset,
        shape = tokens.shapes.small,
        border = BorderStroke(1.dp, tokens.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.s)) {
            ChatMessageHeader(
                row = row,
                colors =
                    ChatHeaderColors(
                        label = tokens.colorScheme.onSurfaceVariant,
                        time = tokens.colorScheme.onSurfaceVariant,
                    ),
                callbacks = callbacks,
                modifier = Modifier.padding(bottom = tokens.spacing.xs),
            )
            ChatMessageBody(row = row, callbacks = callbacks)
        }
    }
}

@Composable
private fun ChatFailedRow(
    row: ChatRow,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = tokens.spacing.s),
        color = tokens.colorScheme.errorContainer,
        shape = tokens.shapes.small,
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.s)) {
            ChatMessageHeader(
                row = row,
                colors =
                    ChatHeaderColors(
                        label = tokens.colorScheme.onErrorContainer,
                        time = tokens.colorScheme.onErrorContainer,
                    ),
                callbacks = callbacks,
                modifier = Modifier.padding(bottom = tokens.spacing.xs),
            )
            row.body?.let { body ->
                MarkdownContent(
                    markdown = body,
                    actions = callbacks.actions,
                    blockSpacing = tokens.spacing.xs,
                    onTableExpand = callbacks.onTableExpand,
                )
            }
            row.failure?.let { failure ->
                Text(
                    text = failure.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            ChatFailureActions(row = row, callbacks = callbacks)
        }
    }
}

@Composable
private fun ChatFailureActions(
    row: ChatRow,
    callbacks: ChatCallbacks,
) {
    val tokens = LocalHermesDesignTokens.current
    var showDetail by remember(row.id) { mutableStateOf(false) }
    val runId = row.runId
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        if (row.retryAvailable && runId != null) {
            ChatTextControl(
                label = stringResource(R.string.action_try_again),
                enabled = callbacks.retryEnabled,
                onClick = { callbacks.onRetry(runId) },
                modifier = Modifier.testTagged("chat-retry-${row.id}"),
            )
        }
        row.technicalDetail?.let {
            ChatTextControl(
                label =
                    stringResource(
                        if (showDetail) R.string.chat_hide_detail else R.string.chat_show_detail,
                    ),
                enabled = true,
                onClick = { showDetail = !showDetail },
                modifier = Modifier.testTagged("chat-detail-${row.id}"),
            )
        }
    }
    if (showDetail) {
        row.technicalDetail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ChatMessageHeader(
    row: ChatRow,
    colors: ChatHeaderColors,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    val tokens = LocalHermesDesignTokens.current
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    val actionsDescription = stringResource(R.string.chat_message_actions, row.id)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.label.resolve().uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.14.em,
            color = colors.label,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        row.timeLabel?.let { time ->
            Text(
                text = time.resolve(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.time,
                maxLines = 1,
                modifier = Modifier.padding(start = tokens.spacing.l),
            )
        }
        Box {
            if (pending) {
                ChatSpinner(modifier = Modifier.padding(start = tokens.spacing.xxs))
            } else {
                ChatDotsControl(
                    description = actionsDescription,
                    onClick = { menuOpen = !menuOpen },
                    modifier =
                        Modifier
                            .padding(start = tokens.spacing.xxs)
                            .testTagged("chat-message-actions-${row.id}"),
                )
            }
            if (menuOpen) {
                ChatMessageMenu(
                    row = row,
                    actions = callbacks.actions,
                    onDismiss = { menuOpen = false },
                )
            }
        }
    }
}

private fun userBubbleShape(): RoundedCornerShape =
    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 4.dp, bottomStart = 10.dp)

private fun Modifier.testTagged(tag: String): Modifier = this.testTag(tag)

private fun Modifier.chatDashedBorder(
    color: Color,
    shape: RoundedCornerShape,
): Modifier =
    drawWithContent {
        drawContent()
        val corner = CornerRadius(shape.topStart.toPx(size, this))
        val outline =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset.Zero, size),
                        topLeft = corner,
                        topRight = corner,
                        bottomRight = corner,
                        bottomLeft = corner,
                    ),
                )
            }
        drawPath(
            path = outline,
            color = color,
            style =
                Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                ),
        )
    }

private const val USER_BUBBLE_FRACTION = 0.82f

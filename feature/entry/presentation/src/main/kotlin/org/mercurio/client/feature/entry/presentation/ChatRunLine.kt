package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * The run line: the state on the left, the dim segment, and either the Gateway state or the one
 * action the reader can take. Stop lives here and nowhere else, so a mis-tap in the composer can
 * never end a Run. The line wraps instead of clipping at a large font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatRunLine(
    model: ChatRunLineState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        Text(
            text = ">",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tokens.colorScheme.primary,
        )
        Text(
            text = model.state.resolve(),
            style = MaterialTheme.typography.labelSmall,
            color = model.stateColor(tokens),
        )
        model.dim?.let { dim ->
            Text(
                text = dim.resolve(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ChatRunRightContent(right = model.right, onEvent = onEvent)
    }
}

@Composable
private fun ChatRunRightContent(
    right: ChatRunRight,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    when (right) {
        is ChatRunRight.Gateway ->
            Text(
                text =
                    stringResource(
                        if (right.online) R.string.chat_gateway_ready else R.string.chat_gateway_offline,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = if (right.online) tokens.colorScheme.primary else tokens.colorScheme.error,
            )
        is ChatRunRight.Action ->
            ChatTextControl(
                label = right.label.resolve(),
                enabled = true,
                onClick = { onEvent(right.event) },
                modifier = Modifier.testTag("chat-run-action"),
            )
    }
}

private fun ChatRunLineState.stateColor(tokens: HermesDesignTokens): Color =
    when {
        error -> tokens.colorScheme.error
        active -> tokens.colorScheme.primary
        else -> tokens.colorScheme.onSurfaceVariant
    }

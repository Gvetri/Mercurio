package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The 40 dp chat bar: the back control, the Session title, and the `::` control the approved
 * design marks at the top right. The bar is its own island; the transcript island carries the
 * rest of the screen.
 */
@Composable
internal fun ChatAppBar(
    session: SessionItemUiState,
    menuEnabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChatBar(
        title = sessionTitle(session),
        backDescription = stringResource(R.string.action_back_to_sessions),
        onBack = { onEvent(EntryUiEvent.ReturnToSessionListClicked) },
        modifier = modifier,
    ) {
        ChatSessionMenuControl(session = session, enabled = menuEnabled, onEvent = onEvent)
    }
}

/** The full-screen table viewer's bar: the same bar, its title naming the table, and a back control. */
@Composable
internal fun ChatTableAppBar(
    title: String,
    session: SessionItemUiState,
    menuEnabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
    onBack: () -> Unit,
) {
    ChatBar(
        title = title,
        backDescription = stringResource(R.string.chat_back_to_transcript),
        onBack = onBack,
    ) {
        ChatSessionMenuControl(session = session, enabled = menuEnabled, onEvent = onEvent)
    }
}

@Composable
private fun ChatBar(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = CHAT_BAR_HEIGHT)
                .padding(start = tokens.spacing.xs, end = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        ChatBackControl(onClick = onBack, description = backDescription)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 0.02.em,
            color = tokens.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.xs)
                    .semantics { heading() },
        )
        trailing()
    }
}

@Composable
private fun ChatSessionMenuControl(
    session: SessionItemUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    var expanded by remember(session.id, enabled) { mutableStateOf(false) }
    val sessionMenuDescription = stringResource(R.string.chat_session_menu)
    Box {
        Box(
            modifier =
                Modifier
                    .widthIn(min = CHAT_TOUCH_TARGET)
                    .heightIn(min = CHAT_TOUCH_TARGET)
                    .semantics { contentDescription = sessionMenuDescription }
                    .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(CHAT_BAR_ACTION_WIDTH)
                        .height(CHAT_BAR_ACTION_HEIGHT)
                        .clip(tokens.shapes.small)
                        .background(tokens.componentColors.inset)
                        .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.shell_connection_actions_mark),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 0.14.em,
                        ),
                    color = tokens.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SessionActionMenuItems(session = session, onDismiss = { expanded = false }, onEvent = onEvent)
        }
    }
}

internal val CHAT_BAR_HEIGHT = 40.dp

private val CHAT_BAR_ACTION_WIDTH = 44.dp

private val CHAT_BAR_ACTION_HEIGHT = 32.dp

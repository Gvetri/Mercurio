package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One 54 dp Session row: the 44 dp last-active column, the title, one preview
 * line, and the trailing affordances. A hold opens the anchored context menu; an
 * inline rename replaces the title cell with the filled check/cross pair.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionRow(
    row: SessionRowUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val inlineRename = row.mutation?.rename != null
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Box {
            SessionRowContent(
                row = row,
                inlineRename = inlineRename,
                held = menuOpen && !inlineRename,
                onLongPress = { menuOpen = true },
                onEvent = onEvent,
            )
            SessionRowMenu(
                expanded = menuOpen && !inlineRename,
                row = row,
                onDismiss = { menuOpen = false },
                onEvent = onEvent,
            )
        }
        SessionRowMutationFeedback(
            session = row.session,
            mutation = row.mutation,
            enabled = row.enabled,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowContent(
    row: SessionRowUiState,
    inlineRename: Boolean,
    held: Boolean,
    onLongPress: () -> Unit,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val component = tokens.componentColors
    val selected = row.selected || inlineRename
    val background =
        when {
            held -> tokens.colorScheme.onSurface.copy(alpha = HELD_LAYER_ALPHA)
            selected -> component.sessionSelected
            else -> Color.Transparent
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SESSION_ROW_MIN_HEIGHT)
                .background(background)
                .combinedClickable(
                    enabled = row.enabled && row.mutation?.pendingAction == null && !inlineRename,
                    onClick = row.onClick,
                    onLongClick = onLongPress,
                )
                .semantics {
                    this.selected = row.selected
                    if (!inlineRename) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Show actions") {
                                    onLongPress()
                                    true
                                },
                            )
                    }
                }
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionTimeColumn(row = row, selected = selected)
        Spacer(modifier = Modifier.width(tokens.spacing.l))
        SessionRowMiddle(
            row = row,
            inlineRename = inlineRename,
            selected = selected,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun RowScope.SessionRowMiddle(
    row: SessionRowUiState,
    inlineRename: Boolean,
    selected: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val rename = row.mutation?.rename
    Column(modifier = Modifier.weight(1f)) {
        if (inlineRename && rename != null) {
            RenameSessionField(
                session = row.session,
                state = rename,
                enabled = row.enabled,
                onEvent = onEvent,
            )
        } else {
            SessionTitleAndPreview(row = row, selected = selected)
        }
    }
    if (inlineRename && rename != null) {
        Spacer(modifier = Modifier.width(tokens.spacing.xs))
        InlineRenameActions(
            session = row.session,
            state = rename,
            enabled = row.enabled,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun SessionTimeColumn(
    row: SessionRowUiState,
    selected: Boolean,
) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = row.session.lastActiveLabel,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) tokens.componentColors.onSessionSelectedDim else tokens.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .width(SESSION_TIME_COLUMN)
                .then(
                    if (row.session.lastActiveLabel == UNKNOWN_LAST_ACTIVE_LABEL) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
private fun SessionTitleAndPreview(
    row: SessionRowUiState,
    selected: Boolean,
) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = row.session.title,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) tokens.componentColors.onSessionSelected else tokens.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val previewText =
        if (row.session.isPendingTitle) PENDING_TITLE_PREVIEW else row.session.preview
    if (previewText != null) {
        Spacer(modifier = Modifier.height(tokens.spacing.xxs))
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color =
                when {
                    row.session.isPendingTitle -> tokens.colorScheme.primary
                    selected -> tokens.componentColors.onSessionSelectedDim
                    else -> tokens.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RenameSessionField(
    session: SessionItemUiState,
    state: SessionRenameUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(session.id) { focusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = state.titleDraft,
            onValueChange = { onEvent(EntryUiEvent.RenameSessionTitleChanged(session.id, it)) },
            enabled = enabled && !state.isSubmitting,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.titleMedium.copy(
                    color = tokens.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
            cursorBrush = SolidColor(tokens.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) },
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Session title" },
        )
        HorizontalDivider(color = tokens.colorScheme.primary, thickness = 2.dp)
        if (session.preview != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.xxs))
            Text(
                text = session.preview,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.componentColors.onSessionSelectedDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlineRenameActions(
    session: SessionItemUiState,
    state: SessionRenameUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs)) {
        RenameAction(
            glyph = "✓",
            description = "Confirm rename",
            positive = true,
            enabled = enabled && !state.isSubmitting,
            onClick = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) },
        )
        RenameAction(
            glyph = "×",
            description = "Cancel rename",
            positive = false,
            enabled = !state.isSubmitting,
            onClick = { onEvent(EntryUiEvent.CancelRenameSessionClicked(session.id)) },
        )
    }
}

@Composable
private fun RenameAction(
    glyph: String,
    description: String,
    positive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val background = if (positive) tokens.colorScheme.primary else tokens.colorScheme.surfaceContainerHighest
    val contentColor = if (positive) tokens.colorScheme.onPrimary else tokens.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .sizeIn(minWidth = RENAME_ACTION_SIZE, minHeight = RENAME_ACTION_SIZE)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}

@Composable
private fun SessionRowMutationFeedback(
    session: SessionItemUiState,
    mutation: SessionMutationUiState?,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    if (mutation?.delete != null) return
    val pending = mutation?.pendingAction
    val errors =
        listOfNotNull(
            mutation?.errorCategory?.safeMessage,
            mutation?.rename?.errorCategory?.safeMessage,
        ).distinct()
    if (pending == null && errors.isEmpty() && mutation?.retryAction == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m)) {
        if (pending != null) {
            Text(
                text = pending.pendingMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        errors.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        val retryEvent =
            when (mutation?.retryAction) {
                SessionMutationAction.PIN -> EntryUiEvent.PinSessionClicked(session.id)
                SessionMutationAction.UNPIN -> EntryUiEvent.UnpinSessionClicked(session.id)
                SessionMutationAction.RENAME, SessionMutationAction.DELETE, null -> null
            }
        retryEvent?.let { event ->
            Box(
                modifier =
                    Modifier
                        .heightIn(min = tokens.spacing.xxxl)
                        .clickable(enabled = enabled && mutation?.pendingAction == null) {
                            onEvent(event)
                        },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Try again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colorScheme.primary,
                )
            }
        }
    }
}

private fun SessionMutationAction.pendingMessage(): String =
    when (this) {
        SessionMutationAction.RENAME -> "Renaming Session…"
        SessionMutationAction.PIN -> "Pinning Session…"
        SessionMutationAction.UNPIN -> "Unpinning Session…"
        SessionMutationAction.DELETE -> "Deleting Session…"
    }

internal const val PENDING_TITLE_PREVIEW = "Generating title…"

private val SESSION_ROW_MIN_HEIGHT = 54.dp
private val SESSION_TIME_COLUMN = 44.dp
private val RENAME_ACTION_SIZE = 32.dp
private const val HELD_LAYER_ALPHA = 0.12f

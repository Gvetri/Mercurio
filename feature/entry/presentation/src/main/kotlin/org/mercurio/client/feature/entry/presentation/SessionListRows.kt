package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.em

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
    val component = tokens.componentColors
    val mutation = row.mutation
    val inlineRename = mutation?.rename != null
    var menuOpen by remember { mutableStateOf(false) }
    val held = menuOpen && !inlineRename
    val rowBackground =
        when {
            held -> tokens.colorScheme.onSurface.copy(alpha = HELD_LAYER_ALPHA)
            row.selected || inlineRename -> component.sessionSelected
            else -> Color.Transparent
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SESSION_ROW_MIN_HEIGHT)
                        .background(rowBackground)
                        .combinedClickable(
                            enabled = row.enabled && mutation?.pendingAction == null && !inlineRename,
                            onClick = row.onClick,
                            onLongClick = { menuOpen = true },
                        )
                        .semantics {
                            this.selected = row.selected
                            if (!inlineRename) {
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction("Show actions") {
                                            menuOpen = true
                                            true
                                        },
                                    )
                            }
                        }
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.session.lastActiveLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.selected) component.onSessionSelectedDim else tokens.colorScheme.onSurfaceVariant,
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
                Spacer(modifier = Modifier.width(tokens.spacing.l))
                Column(modifier = Modifier.weight(1f)) {
                    if (inlineRename && mutation?.rename != null) {
                        RenameSessionField(
                            session = row.session,
                            state = mutation.rename,
                            enabled = row.enabled,
                            onEvent = onEvent,
                        )
                    } else {
                        Text(
                            text = row.session.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (row.selected) component.onSessionSelected else tokens.colorScheme.onSurface,
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
                                        row.selected -> component.onSessionSelectedDim
                                        else -> tokens.colorScheme.onSurfaceVariant
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (inlineRename && mutation?.rename != null) {
                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                    InlineRenameActions(
                        session = row.session,
                        state = mutation.rename,
                        enabled = row.enabled,
                        onEvent = onEvent,
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen && !inlineRename,
                onDismissRequest = { menuOpen = false },
                shape = tokens.shapes.extraSmall,
                containerColor = tokens.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = tokens.elevation.level2,
                modifier = Modifier.width(SESSION_MENU_WIDTH),
            ) {
                SessionMenuItem(
                    label = if (row.session.pinned) "Unpin" else "Pin",
                    enabled = row.enabled && mutation?.pendingAction == null,
                ) {
                    menuOpen = false
                    onEvent(
                        if (row.session.pinned) {
                            EntryUiEvent.UnpinSessionClicked(row.session.id)
                        } else {
                            EntryUiEvent.PinSessionClicked(row.session.id)
                        },
                    )
                }
                SessionMenuItem(
                    label = "Rename",
                    enabled = row.enabled && mutation?.pendingAction == null,
                ) {
                    menuOpen = false
                    onEvent(EntryUiEvent.RenameSessionClicked(row.session.id))
                }
                HorizontalDivider(color = tokens.colorScheme.outlineVariant)
                SessionMenuItem(
                    label = "Delete",
                    enabled = row.enabled && mutation?.pendingAction == null,
                    destructive = true,
                ) {
                    menuOpen = false
                    onEvent(EntryUiEvent.DeleteSessionClicked(row.session.id))
                }
            }
        }
        SessionRowMutationFeedback(
            session = row.session,
            mutation = mutation,
            enabled = row.enabled,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun SessionMenuItem(
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
            keyboardActions = KeyboardActions(onDone = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) }),
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
            background = tokens.colorScheme.primary,
            contentColor = tokens.colorScheme.onPrimary,
            enabled = enabled && !state.isSubmitting,
            onClick = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) },
        )
        RenameAction(
            glyph = "×",
            description = "Cancel rename",
            background = tokens.colorScheme.surfaceContainerHighest,
            contentColor = tokens.colorScheme.onSurfaceVariant,
            enabled = !state.isSubmitting,
            onClick = { onEvent(EntryUiEvent.CancelRenameSessionClicked(session.id)) },
        )
    }
}

@Composable
private fun RenameAction(
    glyph: String,
    description: String,
    background: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
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

@Composable
internal fun SessionSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.16.em),
        color = tokens.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.m,
                    end = tokens.spacing.m,
                    top = 10.dp,
                    bottom = tokens.spacing.xs,
                ),
    )
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
private val SESSION_MENU_WIDTH = 216.dp
private val SESSION_MENU_ITEM_HEIGHT = 44.dp
private val RENAME_ACTION_SIZE = 32.dp
private const val HELD_LAYER_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

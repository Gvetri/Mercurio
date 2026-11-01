package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The 40 dp wordmark bar and the 34 dp search row, the two elements of the top
 * island in the approved Session list design.
 */
@Composable
internal fun SessionListPaneHeader(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.large)
                .background(tokens.colorScheme.surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SESSION_BAR_HEIGHT)
                    .padding(start = spacing.m, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "mercurio",
                style = MaterialTheme.typography.titleSmall,
                letterSpacing = 0.02.em,
                color = tokens.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(SESSION_BAR_CONTROL_WIDTH)
                        .heightIn(min = SESSION_BAR_CONTROL_HEIGHT)
                        .clip(tokens.shapes.small)
                        .background(tokens.componentColors.inset)
                        .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.small)
                        .clickable { onEvent(EntryUiEvent.OpenSettingsClicked) }
                        .semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "::",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 15.sp,
                            letterSpacing = 0.14.em,
                        ),
                    color = tokens.colorScheme.onSurfaceVariant,
                )
            }
        }
        SessionSearchRow(
            state = state,
            onEvent = onEvent,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.s, end = spacing.s, bottom = spacing.s),
        )
    }
}

@Composable
private fun SessionSearchRow(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    Row(
        modifier =
            modifier
                .heightIn(min = SESSION_SEARCH_HEIGHT)
                .clip(tokens.shapes.small)
                .background(tokens.componentColors.inset)
                .border(1.dp, tokens.colorScheme.outlineVariant, tokens.shapes.small)
                .padding(start = 10.dp, end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(EntryUiEvent.SessionSearchQueryChanged(it)) },
            enabled = state.sessionMutations.isEmpty(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.colorScheme.onSurface),
            cursorBrush = SolidColor(tokens.colorScheme.primary),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Search Sessions" },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "search sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (state.searchQuery.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .widthIn(min = SESSION_SEARCH_CLEAR_SIZE)
                        .heightIn(min = SESSION_SEARCH_CLEAR_SIZE)
                        .clip(CircleShape)
                        .clickable { onEvent(EntryUiEvent.ClearSessionSearchClicked) }
                        .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val SESSION_BAR_HEIGHT = 40.dp
private val SESSION_BAR_CONTROL_WIDTH = 44.dp
private val SESSION_BAR_CONTROL_HEIGHT = 32.dp
private val SESSION_SEARCH_HEIGHT = 34.dp
private val SESSION_SEARCH_CLEAR_SIZE = 28.dp

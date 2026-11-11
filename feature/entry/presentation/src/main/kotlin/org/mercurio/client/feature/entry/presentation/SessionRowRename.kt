package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun RenameSessionField(
    session: SessionItemUiState,
    state: SessionRenameUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    val focusRequester = remember { FocusRequester() }
    val sessionTitleDescription = stringResource(R.string.sessions_title_field)
    val focusRequest = remember(session.id) { RenameFocusRequest() }
    SideEffect {
        if (!focusRequest.requested) {
            focusRequest.requested = true
            focusRequester.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        val title = rememberEchoSafeTextFieldBuffer(state.titleDraft, session.id)
        BasicTextField(
            value = title.value,
            onValueChange = { change ->
                if (title.onUserChange(change)) onEvent(EntryUiEvent.RenameSessionTitleChanged(session.id, change.text))
            },
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
                    .semantics { contentDescription = sessionTitleDescription },
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
internal fun InlineRenameActions(
    session: SessionItemUiState,
    state: SessionRenameUiState,
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs)) {
        RenameAction(
            glyph = "✓",
            description = stringResource(R.string.sessions_confirm_rename),
            positive = true,
            enabled = enabled && !state.isSubmitting,
            onClick = { onEvent(EntryUiEvent.ConfirmRenameSessionClicked(session.id)) },
        )
        RenameAction(
            glyph = "×",
            description = stringResource(R.string.sessions_cancel_rename),
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

private class RenameFocusRequest {
    var requested: Boolean = false
}

private val RENAME_ACTION_SIZE = 32.dp

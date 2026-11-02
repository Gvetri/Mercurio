package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The composer at the bottom of the transcript island: the stated reason when sending is not
 * available, the tools row, the `>` prompt, the draft field, and the `[ send ]` text control.
 * The action never turns into Stop; Stop belongs to the run line.
 */
@Composable
internal fun ChatComposer(
    state: OpenSessionUiState,
    composer: ChatComposerState,
    modelPicker: ModelPickerUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("conversation-composer-tail")
                .background(tokens.colorScheme.surface),
    ) {
        HorizontalDivider(thickness = 1.dp, color = tokens.colorScheme.outlineVariant)
        Column(
            modifier =
                Modifier.padding(
                    start = tokens.spacing.s,
                    end = tokens.spacing.s,
                    bottom = tokens.spacing.s,
                ),
        ) {
            ChatComposerReason(state = state, composer = composer, onEvent = onEvent)
            ChatComposerTools(state = state, modelPicker = modelPicker, onEvent = onEvent)
            ChatComposerDraft(state = state, composer = composer, onEvent = onEvent)
        }
    }
}

@Composable
private fun ChatComposerReason(
    state: OpenSessionUiState,
    composer: ChatComposerState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    composer.reason?.let { reason ->
        Text(
            text = reason.resolve(),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(start = tokens.spacing.xxs, end = tokens.spacing.xxs, bottom = tokens.spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.hasUnresolvedSubmission) {
        ChatTextControl(
            label = stringResource(R.string.chat_discard_unsent_record),
            enabled = composer.fieldEnabled,
            onClick = { onEvent(EntryUiEvent.DiscardUnresolvedSendClicked) },
            modifier = Modifier.testTag("conversation-discard-unsent"),
        )
    }
}

@Composable
private fun ChatComposerTools(
    state: OpenSessionUiState,
    modelPicker: ModelPickerUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CHAT_TOOLS_HEIGHT)
                .padding(start = tokens.spacing.xxs, end = tokens.spacing.xxs, bottom = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        ComposerModelPickerSlot(picker = modelPicker, onEvent = onEvent)
        ChatDisabledGlyph(
            painter = painterResource(R.drawable.ic_chat_attach),
            description = stringResource(R.string.chat_attach_unavailable),
        )
        ChatDisabledGlyph(
            painter = painterResource(R.drawable.ic_chat_voice),
            description = stringResource(R.string.chat_voice_unavailable),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state.composerText.lines().size > CHAT_DRAFT_EXPAND_LINES) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_expand),
                contentDescription = stringResource(R.string.chat_open_editor),
                modifier = Modifier.size(CHAT_GLYPH_SIZE),
                tint = tokens.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ChatComposerDraft(
    state: OpenSessionUiState,
    composer: ChatComposerState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = CHAT_TOUCH_TARGET),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
    ) {
        Text(
            text = ">",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tokens.colorScheme.primary,
            modifier = Modifier.padding(bottom = tokens.spacing.xs),
        )
        ChatComposerField(state = state, composer = composer, onEvent = onEvent, modifier = Modifier.weight(1f))
        ChatTextControl(
            label = stringResource(R.string.chat_send),
            enabled = composer.sendEnabled,
            onClick = { onEvent(EntryUiEvent.SendMessageClicked) },
            modifier = Modifier.testTag("conversation-send"),
            contentDescription = stringResource(R.string.chat_send_message),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChatComposerField(
    state: OpenSessionUiState,
    composer: ChatComposerState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val focusRequester = remember { FocusRequester() }
    val handledFocusRequestId = remember { mutableStateOf(0L) }
    LaunchedEffect(state.composerFocusRequestId, composer.fieldEnabled) {
        val requested = state.composerFocusRequestId
        if (requested > handledFocusRequestId.value && composer.fieldEnabled) {
            handledFocusRequestId.value = requested
            focusRequester.requestFocus()
        }
    }
    val draftColor =
        if (composer.fieldEnabled) {
            tokens.colorScheme.onSurface
        } else {
            tokens.colorScheme.onSurface.copy(alpha = CHAT_DISABLED_ALPHA)
        }
    BasicTextField(
        value = state.composerText,
        onValueChange = { onEvent(EntryUiEvent.ComposerTextChanged(it)) },
        enabled = composer.fieldEnabled,
        singleLine = false,
        maxLines = CHAT_DRAFT_MAX_LINES,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = draftColor),
        cursorBrush = SolidColor(tokens.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions =
            KeyboardActions(
                onSend = { if (composer.sendEnabled) onEvent(EntryUiEvent.SendMessageClicked) },
            ),
        modifier =
            modifier
                .heightIn(max = CHAT_DRAFT_MAX_HEIGHT)
                .padding(bottom = tokens.spacing.xs)
                .focusRequester(focusRequester)
                .testTag("conversation-message-input")
                .semantics { testTagsAsResourceId = true },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (state.composerText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

private val CHAT_DRAFT_MAX_HEIGHT = 100.dp

private val CHAT_TOOLS_HEIGHT = 24.dp

private const val CHAT_DRAFT_MAX_LINES = 5

private const val CHAT_DRAFT_EXPAND_LINES = 5

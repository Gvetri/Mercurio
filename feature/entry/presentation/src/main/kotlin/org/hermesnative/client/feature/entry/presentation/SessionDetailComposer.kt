package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.hermesnative.client.feature.entry.domain.RunPresentationState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SessionDetailComposerTail(
    state: OpenSessionUiState,
    mutation: SessionMutationUiState?,
    canSubmit: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tailScrollState = rememberScrollState()
    val composerEnabled = !state.isRefreshing && mutation?.pendingAction == null
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tailMaxHeight = paneTailMaxHeight(maxHeight)
        Column(modifier = composerTailModifier(tailMaxHeight, tailScrollState)) {
            if (state.isSending) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sending message…",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            state.sendErrorCategory?.let { category ->
                ComposerSendErrorRow(
                    category = category,
                    showDiscard = state.hasUnresolvedSubmission,
                    discardEnabled = composerEnabled,
                    onEvent = onEvent,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val sendEnabled =
                composerEnabled &&
                    !state.isReconciliationInProgress &&
                    state.latestRunState != RunPresentationState.UNCERTAIN &&
                    canSubmit &&
                    state.composerText.isNotBlank()
            OutlinedTextField(
                value = state.composerText,
                onValueChange = { onEvent(EntryUiEvent.ComposerTextChanged(it)) },
                label = { Text("Message") },
                placeholder = { Text("Write a message") },
                modifier =
                    Modifier.fillMaxWidth().testTag("conversation-message-input").semantics {
                        testTagsAsResourceId = true
                    },
                enabled = composerEnabled,
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            if (sendEnabled) onEvent(EntryUiEvent.SendMessageClicked)
                        },
                    ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ComposerSendButton(state = state, sendEnabled = sendEnabled, onEvent = onEvent)
        }
    }
}

private fun composerTailModifier(
    tailMaxHeight: Dp,
    scrollState: ScrollState,
): Modifier =
    Modifier.testTag("conversation-composer-tail")
        .fillMaxWidth()
        .heightIn(max = tailMaxHeight)
        .verticalScroll(scrollState)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ComposerSendButton(
    state: OpenSessionUiState,
    sendEnabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    Button(
        onClick = { onEvent(EntryUiEvent.SendMessageClicked) },
        enabled = sendEnabled,
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("conversation-send").semantics { testTagsAsResourceId = true },
    ) {
        Text(
            text =
                when {
                    state.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN ||
                        state.hasUnresolvedSubmission -> "Refresh history to resolve"
                    state.sendErrorCategory != null -> "Try again"
                    else -> "Send"
                },
        )
    }
}

@Composable
private fun ComposerSendErrorRow(
    category: MessageSendErrorCategory,
    showDiscard: Boolean,
    discardEnabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.safeMessage,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier.weight(1f, fill = false)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
        )
        if (showDiscard) {
            ComposerDiscardButton(enabled = discardEnabled, onEvent = onEvent)
        }
    }
}

@Composable
private fun ComposerDiscardButton(
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    TextButton(
        onClick = { onEvent(EntryUiEvent.DiscardUnresolvedSendClicked) },
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(text = "Discard unsent record")
    }
}

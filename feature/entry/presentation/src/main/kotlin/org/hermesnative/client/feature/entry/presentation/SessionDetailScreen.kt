package org.hermesnative.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.hermesnative.client.feature.entry.domain.RunSubmissionState

@Composable
private fun SessionDetailHeader(state: OpenSessionUiState) {
    Text(
        text = state.session.title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    state.session.preview?.let { preview ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = preview, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SessionDetailContent(
    detail: SessionDetailState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = detail.content
    val displayedMessages = state.messages + listOfNotNull(state.activeResponse)
    val canSubmit = submissionCanSubmit(detail)
    val transcriptListState = rememberLazyListState()
    var trailingContentHeight by remember(state.session.id, displayedMessages.lastOrNull()?.id) { mutableIntStateOf(0) }
    val textLineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.roundToPx() }
    val viewportHeight by remember { derivedStateOf { transcriptListState.layoutInfo.viewportSize.height } }
    val hiddenTail = (trailingContentHeight + textLineHeight - viewportHeight).coerceIn(0, trailingContentHeight)
    TranscriptScrollEffects(
        detail = detail,
        displayedMessages = displayedMessages,
        listState = transcriptListState,
        hiddenTail = hiddenTail,
    )
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        SessionDetailToolbar(detail = detail, onEvent = onEvent)
        SessionDetailTranscript(
            detail = detail,
            displayedMessages = displayedMessages,
            listState = transcriptListState,
            onEvent = onEvent,
            onTrailingContentHeightChanged = { trailingContentHeight = it },
        )
        SessionDetailComposerTail(
            state = state,
            mutation = detail.mutation,
            canSubmit = canSubmit,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDetailToolbar(
    detail: SessionDetailState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = { onEvent(EntryUiEvent.ReturnToSessionListClicked) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(text = "Back to Sessions")
        }
        SessionActionMenu(
            session = detail.content.session,
            enabled =
                detail.actionsEnabled &&
                    detail.mutation?.pendingAction == null &&
                    detail.mutation?.rename == null &&
                    detail.mutation?.delete == null,
            onEvent = onEvent,
        )
    }
}

private fun submissionCanSubmit(detail: SessionDetailState): Boolean =
    RunSubmissionState(
        latestRun = detail.content.latestRun,
        activeRuns = detail.content.activeRuns,
        isSubmissionPending = detail.content.isSending || detail.listRequestActive,
    ).canSubmit

@Composable
private fun ColumnScope.SessionDetailTranscript(
    detail: SessionDetailState,
    displayedMessages: List<SessionMessageUiState>,
    listState: LazyListState,
    onEvent: (EntryUiEvent) -> Unit,
    onTrailingContentHeightChanged: (Int) -> Unit,
) {
    val retryEnabled = detail.retryEnabled(submissionCanSubmit(detail))
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SessionDetailHeader(state = detail.content)
            Spacer(modifier = Modifier.height(12.dp))
            SessionDetailSummary(
                detail = detail,
                retryEnabled = retryEnabled,
                onEvent = onEvent,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (displayedMessages.isEmpty()) {
                Text(text = "No messages in this Session.")
            }
        }
        items(
            items = displayedMessages,
            key = { message -> message.id },
        ) { message ->
            key(detail.content.session.id) {
                SessionMessageContent(
                    message = message,
                    retryEnabled = retryEnabled,
                    onRetry = { runId -> onEvent(EntryUiEvent.RetryRunClicked(runId)) },
                    onTrailingContentHeightChanged = {
                        if (message.id == displayedMessages.last().id) onTrailingContentHeightChanged(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun TranscriptScrollEffects(
    detail: SessionDetailState,
    displayedMessages: List<SessionMessageUiState>,
    listState: LazyListState,
    hiddenTail: Int,
) {
    val sessionId = detail.content.session.id
    val newestMessageItemIndex = displayedMessages.size
    val followNewestMessages = rememberTranscriptFollow(listState, hiddenTail)
    var transcriptPositionPending by remember(sessionId) { mutableStateOf(true) }
    val streamedResponseContent = detail.content.activeResponse?.content
    LaunchedEffect(sessionId, newestMessageItemIndex, streamedResponseContent, hiddenTail) {
        if (newestMessageItemIndex == 0) return@LaunchedEffect
        if (transcriptPositionPending) {
            listState.pinNewestItemEnd(newestMessageItemIndex, hiddenTail)
            transcriptPositionPending = false
        } else if (followNewestMessages) {
            listState.pinNewestItemEnd(newestMessageItemIndex, hiddenTail)
        }
    }
    val showMutationContext =
        detail.mutation?.rename != null ||
            detail.mutation?.delete != null ||
            detail.mutation?.errorCategory != null
    LaunchedEffect(sessionId, showMutationContext) {
        if (showMutationContext && displayedMessages.isNotEmpty()) listState.scrollToItem(0)
    }
}

private suspend fun LazyListState.pinNewestItemEnd(
    index: Int,
    hiddenTail: Int,
) {
    if (layoutInfo.visibleItemsInfo.none { item -> item.index == index }) {
        scrollToItem(index)
    }
    val viewportHeight = layoutInfo.viewportEndOffset
    val itemSize = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == index }?.size ?: return
    scrollToItem(index, (itemSize - viewportHeight - hiddenTail).coerceAtLeast(0))
}

/**
 * Whether the transcript should follow its newest content. The follow stays on while the
 * newest content's end is visible, turns off when the reader scrolls it out of view, and
 * returns once the transcript shows the newest content again, so a reader who scrolled up
 * is not pulled away from what they are reading. [hiddenTail] excludes only the trailing
 * metadata that would otherwise hide the newest text line in a short viewport.
 */
@Composable
internal fun rememberTranscriptFollow(
    listState: LazyListState,
    hiddenTail: Int,
): Boolean {
    val follow by
        remember(listState, hiddenTail) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val newestItem = layoutInfo.visibleItemsInfo.lastOrNull()
                layoutInfo.totalItemsCount == 0 ||
                    (
                        newestItem != null &&
                            newestItem.index == layoutInfo.totalItemsCount - 1 &&
                            newestItem.offset.toLong() + newestItem.size.toLong() - hiddenTail <=
                            layoutInfo.viewportEndOffset.toLong()
                    )
            }
        }
    return follow
}

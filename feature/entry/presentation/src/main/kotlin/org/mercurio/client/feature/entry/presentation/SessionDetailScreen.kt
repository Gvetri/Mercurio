package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.mercurio.client.feature.entry.domain.RunId

/**
 * The approved Chat composition: the 40 dp bar island, and one transcript island that holds the
 * messages, the run line and the composer. The derivation belongs to [chatTranscriptItems],
 * [chatRunLineState] and [chatComposerState]; this file only arranges what they return.
 */
@Composable
internal fun SessionDetailContent(
    detail: SessionDetailState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val state = detail.content
    val listState = rememberLazyListState()
    val displayed =
        remember(state.messages, state.activeResponse) {
            state.messages + listOfNotNull(state.activeResponse)
        }
    val items = remember(displayed) { chatTranscriptItems(displayed) }
    val messageActions = rememberMessageActions()
    val callbacks =
        remember(detail, messageActions) {
            ChatCallbacks(
                actions = messageActions,
                retryEnabled = detail.retryEnabled(chatCanSubmit(detail)),
                onRetry = { runId: RunId -> onEvent(EntryUiEvent.RetryRunClicked(runId)) },
                onEvent = onEvent,
            )
        }
    val followNewest = rememberTranscriptFollow(listState)
    LaunchedEffect(items) {
        if (items.isNotEmpty() && followNewest) listState.pinNewestItemEnd(items.lastIndex)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(CHAT_ISLAND_GUTTER),
        verticalArrangement = Arrangement.spacedBy(CHAT_ISLAND_GUTTER),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colorScheme.surface,
            shape = tokens.shapes.large,
        ) {
            ChatAppBar(
                session = state.session,
                menuEnabled = detail.actionsEnabled && detail.mutation?.pendingAction == null,
                onEvent = onEvent,
            )
        }
        ChatMainIsland(
            detail = detail,
            items = items,
            listState = listState,
            callbacks = callbacks,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun ChatMainIsland(
    detail: SessionDetailState,
    items: List<ChatTranscriptItem>,
    listState: LazyListState,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Surface(
        modifier = modifier,
        color = tokens.colorScheme.surface,
        shape = tokens.shapes.large,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tail = maxHeight * CHAT_TAIL_FRACTION
            Column(modifier = Modifier.fillMaxSize()) {
                ChatTranscriptRegion(
                    detail = detail,
                    items = items,
                    listState = listState,
                    callbacks = callbacks,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = tail)
                            .verticalScroll(rememberScrollState()),
                ) {
                    ChatMutationPanel(detail = detail, onEvent = callbacks.onEvent)
                    ChatRunLine(model = remember(detail) { chatRunLineState(detail) }, onEvent = callbacks.onEvent)
                    ChatComposer(
                        state = detail.content,
                        composer = remember(detail) { chatComposerState(detail) },
                        modelPicker = detail.modelPicker,
                        onEvent = callbacks.onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTranscriptRegion(
    detail: SessionDetailState,
    items: List<ChatTranscriptItem>,
    listState: LazyListState,
    callbacks: ChatCallbacks,
    modifier: Modifier = Modifier,
) {
    val state = detail.content
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            items.isNotEmpty() ->
                ChatTranscript(
                    items = items,
                    listState = listState,
                    callbacks = callbacks,
                    sessionKey = state.session.id,
                )
            state.isRefreshing ->
                ChatEmptyState(
                    title = "Loading Session history…",
                    body = "The Gateway is returning this Session.",
                )
            state.errorCategory != null && !state.isCached ->
                ChatEmptyState(
                    title = "Session history is unavailable",
                    body = "The Gateway could not be read. No messages are shown.",
                    actionLabel = "[ try again ]",
                    onAction = { callbacks.onEvent(EntryUiEvent.RefreshSessionsClicked) },
                )
            else ->
                ChatEmptyState(
                    title = "No messages yet",
                    body = "This Session has no history on the Gateway. Write the first message.",
                )
        }
    }
}

@Composable
private fun ChatMutationPanel(
    detail: SessionDetailState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val mutation = detail.mutation
    val visible = mutation?.rename != null || mutation?.delete != null || mutation?.errorCategory != null
    if (visible) {
        ChatMutationContent(detail = detail, mutation = mutation, onEvent = onEvent)
    }
}

/**
 * The empty, loading and unavailable state: one title, one body, and at most one action, as the
 * approved design draws it. No state pretends history exists.
 */
@Composable
internal fun ChatEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier = modifier.fillMaxSize().padding(tokens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (actionLabel != null && onAction != null) {
            ChatTextControl(
                label = actionLabel,
                enabled = true,
                onClick = onAction,
                modifier = Modifier.padding(top = tokens.spacing.s),
            )
        }
    }
}

/**
 * Whether the transcript follows its newest content. The follow stays on while the newest content
 * is on screen, turns off when a settled scroll leaves it out of view, and returns once the reader
 * settles back at the newest content.
 */
@Composable
internal fun rememberTranscriptFollow(
    listState: LazyListState,
    hiddenTail: Int = 0,
): Boolean {
    var follow by remember(listState) { mutableStateOf(true) }
    LaunchedEffect(listState, hiddenTail) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect {
                val layoutInfo = listState.layoutInfo
                if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    follow = layoutInfo.showsNewestContent(hiddenTail)
                }
            }
    }
    return follow
}

private fun LazyListLayoutInfo.showsNewestContent(hiddenTail: Int): Boolean {
    val newestItem = visibleItemsInfo.lastOrNull()
    val isNewest = newestItem != null && newestItem.index == totalItemsCount - 1
    val newestIsOnScreen = isNewest && newestItem.offset < viewportEndOffset - hiddenTail
    return totalItemsCount == 0 || newestIsOnScreen
}

private suspend fun LazyListState.pinNewestItemEnd(index: Int) {
    repeat(PIN_ATTEMPTS) {
        withFrameNanos { }
        val info = layoutInfo
        if (info.viewportEndOffset == 0) return@repeat
        val item = info.visibleItemsInfo.firstOrNull { visible -> visible.index == index }
        if (item == null) {
            scrollToItem(index)
            return@repeat
        }
        val distanceToEnd = item.offset + item.size - info.viewportEndOffset
        if (distanceToEnd <= 0) return
        scrollToItem(index, item.offset + distanceToEnd)
    }
}

internal val CHAT_ISLAND_GUTTER = 4.dp

private const val CHAT_TAIL_FRACTION = 0.6f

private const val PIN_ATTEMPTS = 3

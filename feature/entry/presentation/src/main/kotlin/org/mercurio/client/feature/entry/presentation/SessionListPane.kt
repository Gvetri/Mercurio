package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mercurio.client.feature.entry.domain.SessionId

/**
 * The approved Session list composition: the wordmark island, the list island,
 * and the status strip. Pull-to-refresh re-reads the first page; the list end
 * loads the next page through its footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionListPane(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    selectedSessionId: SessionId? = null,
    notice: InboundLinkNoticeCategory? = null,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(ISLAND_GUTTER),
        verticalArrangement = Arrangement.spacedBy(ISLAND_GUTTER),
    ) {
        SessionListPaneHeader(state = state, onEvent = onEvent)
        notice?.let { category ->
            InboundLinkNoticeText(notice = category)
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(EntryUiEvent.RefreshSessionListClicked) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = tokens.colorScheme.surface,
                shape = tokens.shapes.large,
            ) {
                SessionListBody(
                    state = state,
                    onEvent = onEvent,
                    selectedSessionId = selectedSessionId,
                )
            }
        }
        SessionListStatusStrip(state = state)
    }
}

@Composable
private fun SessionListBody(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    selectedSessionId: SessionId?,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.searchQuery) {
        if (state.searchQuery.isEmpty()) listState.scrollToItem(0)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pinsActionRow = maxHeight >= PINNED_ACTION_ROW_MIN_ISLAND_HEIGHT
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("session-list"),
            ) {
                sessionListContent(
                    state = state,
                    onEvent = onEvent,
                    selectedSessionId = selectedSessionId,
                    showActionRowInList = !pinsActionRow,
                )
            }
            if (pinsActionRow) {
                NewSessionRow(
                    enabled = state.createSessionActionEnabled(),
                    onEvent = onEvent,
                    modifier = Modifier.testTag(NEW_SESSION_ROW_TAG),
                )
            }
        }
    }
}

private fun LazyListScope.sessionListContent(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    selectedSessionId: SessionId?,
    showActionRowInList: Boolean,
) {
    val mutationOnly =
        state.sessions.filter { session ->
            session.id in state.sessionMutations && state.visibleSessions.none { it.id == session.id }
        }
    when {
        state.isLoading && state.sessions.isEmpty() -> {
            sessionListSkeletonItems()
            item(key = "loading-footer") {
                SessionListFooter(label = stringResource(R.string.sessions_loading))
            }
            if (showActionRowInList) {
                newSessionListItem(state = state, onEvent = onEvent)
            }
        }
        state.visibleSessions.isEmpty() -> {
            item(key = "empty") {
                SessionListEmptyItem(
                    state = state,
                    onEvent = onEvent,
                    showActionRow = showActionRowInList,
                )
            }
        }
        else -> {
            sessionRows(
                sessions = state.visibleSessions + mutationOnly,
                state = state,
                selectedSessionId = selectedSessionId,
                onEvent = onEvent,
                showActionRowInList = showActionRowInList,
            )
        }
    }
}

@Composable
private fun LazyItemScope.SessionListEmptyItem(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    showActionRow: Boolean,
) {
    Column(modifier = Modifier.fillParentMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            SessionListEmptyContent(state = state, onEvent = onEvent)
        }
        if (showActionRow) {
            NewSessionRow(
                enabled = state.createSessionActionEnabled(),
                onEvent = onEvent,
                modifier = Modifier.testTag(NEW_SESSION_ROW_TAG),
            )
        }
    }
}

private fun LazyListScope.sessionRows(
    sessions: List<SessionItemUiState>,
    state: SessionListUiState,
    selectedSessionId: SessionId?,
    onEvent: (EntryUiEvent) -> Unit,
    showActionRowInList: Boolean,
) {
    val pinned = sessions.filter { it.pinned }
    val unpinned = sessions.filterNot { it.pinned }
    if (pinned.isNotEmpty()) {
        item(key = "pinned-header") {
            SessionSectionHeader(label = stringResource(R.string.sessions_pinned_header))
        }
        items(items = pinned, key = { session -> session.id.value }) { session ->
            SessionRow(
                row = sessionRowState(session, state, selectedSessionId, onEvent),
                onEvent = onEvent,
            )
        }
    }
    item(key = "all-sessions-header") {
        SessionSectionHeader(label = stringResource(R.string.sessions_all_header, unpinned.size))
    }
    items(items = unpinned, key = { session -> session.id.value }) { session ->
        SessionRow(
            row = sessionRowState(session, state, selectedSessionId, onEvent),
            onEvent = onEvent,
        )
    }
    if (showActionRowInList) {
        newSessionListItem(state = state, onEvent = onEvent)
    }
    item(key = "creation-error") { SessionCreationErrorBanner(state = state) }
    item(key = "footer") { SessionListFooterTrigger(state = state, onEvent = onEvent) }
}

private fun LazyListScope.newSessionListItem(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    item(key = "new-session") {
        NewSessionRow(
            enabled = state.createSessionActionEnabled(),
            onEvent = onEvent,
            modifier = Modifier.testTag(NEW_SESSION_ROW_TAG),
        )
    }
}

private fun sessionRowState(
    session: SessionItemUiState,
    state: SessionListUiState,
    selectedSessionId: SessionId?,
    onEvent: (EntryUiEvent) -> Unit,
): SessionRowUiState =
    SessionRowUiState(
        session = session,
        mutation = state.sessionMutations[session.id],
        selected = session.id == selectedSessionId,
        enabled = state.allowsSessionOpen(session.id, linked = false),
        mutationEnabled = state.allowsSessionMutation(),
        onClick = { onEvent(EntryUiEvent.SessionClicked(session.id)) },
    )

@Composable
private fun SessionListFooterTrigger(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val canLoadMore =
        state.nextOffset != null &&
            !state.isLoadingMore &&
            !state.isUnavailable &&
            state.sessionMutations.isEmpty()
    LaunchedEffect(canLoadMore, state.nextOffset) {
        if (canLoadMore) onEvent(EntryUiEvent.LoadMoreSessionsClicked)
    }
    when {
        state.isLoadingMore -> SessionListFooter(label = stringResource(R.string.sessions_loading_more))
        state.isLoading -> SessionListFooter(label = stringResource(R.string.sessions_loading))
        else -> Unit
    }
}

private val ISLAND_GUTTER = 6.dp
private val PINNED_ACTION_ROW_MIN_ISLAND_HEIGHT = 97.dp

internal fun paneTailMaxHeight(available: Dp): Dp = (available - 120.dp).coerceAtLeast(minOf(48.dp, available))

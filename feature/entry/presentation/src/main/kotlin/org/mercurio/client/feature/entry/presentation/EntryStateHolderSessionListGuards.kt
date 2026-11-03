package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId

internal fun SessionListUiState.blocksFirstPageLoad(): Boolean =
    (isLoading && !isSearching) ||
        isRefreshing ||
        openingSessionId != null ||
        createSession != null ||
        hasPendingMutation ||
        openedSession?.isRefreshing == true ||
        openedSession?.isReconciliationInProgress == true

internal fun SessionListUiState.blocksLoadingMore(): Boolean =
    isLoading ||
        isRefreshing ||
        isSearching ||
        isLoadingMore ||
        isUnavailable ||
        openedSession?.isRefreshing == true ||
        openedSession?.isReconciliationInProgress == true ||
        openingSessionId != null ||
        createSession != null ||
        sessionMutations.isNotEmpty()

internal fun SessionListUiState.blocksCreateSession(): Boolean =
    openingSessionId != null ||
        isUnavailable ||
        createSession != null ||
        hasPendingMutation

private fun SessionListUiState.blocksSessionTransition(): Boolean =
    hasPendingMutation ||
        openingSessionId != null ||
        createSession != null

internal fun SessionListUiState.blocksSessionOpen(): Boolean =
    isUnavailable ||
        blocksSessionTransition()

/**
 * A linked Session open reads the authoritative Session resource, so a list
 * request in flight does not block it. Only another Session transition does.
 */
internal fun SessionListUiState.blocksLinkedSessionOpen(): Boolean = blocksSessionTransition()

internal fun SessionListUiState.allowsSessionOpen(
    sessionId: SessionId,
    linked: Boolean,
): Boolean {
    val canOpen = linked || sessions.any { it.id == sessionId }
    val blocked = if (linked) blocksLinkedSessionOpen() else blocksSessionOpen()
    return canOpen && !blocked && sessionMutations[sessionId]?.pendingAction == null
}

internal fun SessionListUiState.interruptsListRequest(): Boolean {
    return isLoading || isRefreshing || isSearching || isLoadingMore
}

internal fun EntryStateHolder.blocksRunSubmission(
    opened: OpenSessionUiState,
    sessionId: SessionId,
): Boolean =
    opened.latestRunState == RunPresentationState.UNCERTAIN ||
        opened.sendErrorCategory == MessageSendErrorCategory.UNCERTAIN ||
        opened.hasUnresolvedSubmission ||
        opened.isRefreshing ||
        opened.isReconciliationInProgress ||
        sessionId in connectionRecoverySessionCounts ||
        sessionId in recoverySessionCounts ||
        recoveryLoadPending ||
        recoveryLoadFailed

internal typealias MutationFailureTarget = Pair<SessionListUiState, SessionMutationUiState>

internal fun EntryStateHolder.mutationFailureTarget(request: SessionMutationRequest): MutationFailureTarget? {
    if (!isCurrentSessionMutation(request)) return null
    val current = mutableUiState.value.sessionList
    val mutation = current?.sessionMutations?.get(request.sessionId)
    return if (current == null || mutation == null) null else current to mutation
}

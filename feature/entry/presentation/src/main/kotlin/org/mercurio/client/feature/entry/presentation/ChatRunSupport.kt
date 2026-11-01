package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunSubmissionState

/**
 * The facts the run line and the composer both need, read from the Session state. Each one is a
 * single question with a single answer, so the two surfaces cannot disagree about the same Session.
 */
internal fun SessionDetailState.chatGatewayOnline(): Boolean =
    listErrorCategory != SessionListErrorCategory.GATEWAY_UNAVAILABLE && !content.isHistoryUnavailable()

internal fun chatCanSubmit(detail: SessionDetailState): Boolean =
    RunSubmissionState(
        latestRun = detail.content.latestRun,
        activeRuns = detail.content.activeRuns,
        isSubmissionPending = detail.content.isSending || detail.listRequestActive,
    ).canSubmit

internal fun OpenSessionUiState.chatMessageCount(): Int = messages.size + listOfNotNull(activeResponse).size

internal fun OpenSessionUiState.chatPendingCount(): Int = messages.count { it.id.startsWith(CHAT_LOCAL_MESSAGE_PREFIX) }

/** The last read of the Gateway did not land, so a cached view cannot be trusted as current. */
internal fun OpenSessionUiState.chatRefreshFailed(): Boolean = isStale || errorCategory != null

/** No history is held and the Gateway read failed, so the screen may not claim an empty Session. */
internal fun OpenSessionUiState.isHistoryUnavailable(): Boolean {
    val noContent = chatMessageCount() == 0
    return errorCategory != null && !isCached && noContent
}

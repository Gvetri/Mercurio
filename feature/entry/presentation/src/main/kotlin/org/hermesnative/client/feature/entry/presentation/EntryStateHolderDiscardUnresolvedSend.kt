package org.hermesnative.client.feature.entry.presentation

/**
 * The failed-send recovery contract: an unresolved submission record stays until the
 * user resolves it by retrying, or explicitly discards it. Discarding removes only this
 * client's record; the composer text is kept so the user can resend or edit it.
 */
internal fun EntryStateHolder.showDiscardUnresolvedSendConfirmation() {
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value.sessionList ?: return
        val opened = current.openedSession ?: return
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        openedSession =
                            opened.copy(isDiscardUnresolvedSendConfirmationOpen = true),
                    ),
            )
    }
}

internal fun EntryStateHolder.cancelDiscardUnresolvedSend() {
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value.sessionList ?: return
        val opened = current.openedSession ?: return
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        openedSession =
                            opened.copy(isDiscardUnresolvedSendConfirmationOpen = false),
                    ),
            )
    }
}

internal fun EntryStateHolder.confirmDiscardUnresolvedSend() {
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value.sessionList ?: return
        val opened = current.openedSession ?: return
        val sessionId = opened.session.id
        runSubmissionUncertaintyStore.remove(pendingRunSubmissionKey(sessionId), attemptId = null)
        unresolvedSubmissionSessions.remove(sessionId)
        ambiguousSubmissionSessions.remove(sessionId)
        pendingTimedOutSends.remove(sessionId)
        uncertainSendDrafts.remove(sessionId)
        sessionSendErrors.remove(sessionId)
        mutableUiState.value =
            mutableUiState.value.copy(
                sessionList =
                    current.copy(
                        openedSession =
                            opened.copy(
                                isDiscardUnresolvedSendConfirmationOpen = false,
                                sendErrorCategory = sendErrorCategoryFor(sessionId),
                                hasUnresolvedSubmission = hasUnresolvedSubmission(sessionId),
                            ),
                    ),
            )
    }
}

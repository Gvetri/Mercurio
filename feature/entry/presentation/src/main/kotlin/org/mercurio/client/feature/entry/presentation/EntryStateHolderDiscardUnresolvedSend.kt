package org.mercurio.client.feature.entry.presentation

/**
 * The failed-send recovery contract: an unresolved send stays marked until the user resolves it
 * by retrying, or explicitly clears the local warning. Clearing removes only this device's
 * warning; the composer text is kept so the user can resend or edit it.
 */
internal fun EntryStateHolder.showDiscardUnresolvedSendConfirmation() {
    run {
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
    run {
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
    run {
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

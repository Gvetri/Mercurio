package org.mercurio.client.feature.entry.presentation

/** The composer's enabled state, its stated reason, and whether the action can run. */
internal data class ChatComposerState(
    val fieldEnabled: Boolean,
    val sendEnabled: Boolean,
    val reason: UiText?,
)

/**
 * The composer's contract in one place: a disabled field always states why, and the action
 * is never `[ stop ]`. Stop belongs to the run line, so the two actions never share a slot.
 */
internal fun chatComposerState(detail: SessionDetailState): ChatComposerState {
    val state = detail.content
    val fieldEnabled = state.chatFieldEnabled()
    val sendEnabled =
        fieldEnabled && chatCanSubmit(detail) && !state.chatStreamingRun() && state.composerText.isNotBlank()
    return ChatComposerState(
        fieldEnabled = fieldEnabled,
        sendEnabled = sendEnabled,
        reason = state.chatComposerReason(),
    )
}

/** The first fetch has not answered yet, so there is nothing to send against. */
internal fun OpenSessionUiState.chatLoadingFirstHistory(): Boolean = isRefreshing && chatMessageCount() == 0

private fun OpenSessionUiState.chatFieldEnabled(): Boolean =
    !isSending &&
        !isReconciliationInProgress &&
        !chatLoadingFirstHistory() &&
        !chatStaleRun()

private fun OpenSessionUiState.chatComposerReason(): UiText? =
    when {
        isSending -> UiText.Resource(R.string.chat_reason_waiting_accept)
        isReconciliationInProgress -> UiText.Resource(R.string.chat_reason_reconciling)
        chatLoadingFirstHistory() -> UiText.Resource(R.string.chat_reason_history_loading)
        isRefreshing -> UiText.Resource(R.string.chat_reason_refreshing)
        chatStaleRun() -> UiText.Resource(R.string.chat_reason_stale_run)
        sendErrorCategory != null -> UiText.Resource(sendErrorCategory.messageRes)
        hasUnresolvedSubmission -> UiText.Resource(R.string.chat_reason_stale_run)
        else -> null
    }

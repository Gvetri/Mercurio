package org.mercurio.client.feature.entry.presentation

/** The composer's enabled state, its stated reason, and whether the action can run. */
internal data class ChatComposerState(
    val fieldEnabled: Boolean,
    val sendEnabled: Boolean,
    val reason: String?,
)

internal const val CHAT_PLACEHOLDER = "write a message"

internal const val CHAT_SEND_LABEL = "[ send ]"

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

private fun OpenSessionUiState.chatComposerReason(): String? =
    when {
        isSending -> "Waiting for the Gateway to accept the message."
        isReconciliationInProgress -> "Reconciling the Run state."
        chatLoadingFirstHistory() -> "Sending is unavailable until the history loads."
        isRefreshing -> "Refreshing history."
        chatStaleRun() -> "The Run result may be stale. Refresh history to resolve."
        sendErrorCategory != null -> sendErrorCategory.safeMessage
        hasUnresolvedSubmission -> "The Run result may be stale. Refresh history to resolve."
        else -> null
    }

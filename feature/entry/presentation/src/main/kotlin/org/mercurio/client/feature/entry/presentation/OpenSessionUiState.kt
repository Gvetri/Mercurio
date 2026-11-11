package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunPresentationState

data class OpenSessionUiState(
    val session: SessionItemUiState,
    val messages: List<SessionMessageUiState>,
    val composerText: String = "",
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val errorCategory: SessionHistoryErrorCategory? = null,
    val latestRun: Run? = null,
    val activeRuns: List<Run> = emptyList(),
    val isSending: Boolean = false,
    val sendErrorCategory: MessageSendErrorCategory? = null,
    val hasUnresolvedSubmission: Boolean = false,
    val isDiscardUnresolvedSendConfirmationOpen: Boolean = false,
    val latestRunState: RunPresentationState? = null,
    val latestRunRetryAvailable: Boolean = false,
    val activeResponse: SessionMessageUiState? = null,
    val isReconciliationInProgress: Boolean = false,
    val composerFocusRequestId: Long = 0,
)

/** A Run owns the screen: its answer streams, or its state is still starting or completing. */
internal fun OpenSessionUiState.chatStreamingRun(): Boolean =
    activeResponse?.isStreaming == true ||
        latestRunState == RunPresentationState.RUNNING ||
        latestRunState == RunPresentationState.STARTING ||
        latestRunState == RunPresentationState.COMPLETING

/** What the screen shows may not match the Gateway, so the run line says `stale`. */
internal fun OpenSessionUiState.chatStaleRun(): Boolean =
    latestRun != null &&
        (
            isStale ||
                latestRunState == RunPresentationState.UNCERTAIN ||
                activeResponse?.streamInterrupted == true
        )

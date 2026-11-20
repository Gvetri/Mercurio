package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunPresentationState

internal data class OpenSessionOverlay(
    val composerText: String = "",
    val sendErrorCategory: MessageSendErrorCategory? = null,
    val hasUnresolvedSubmission: Boolean = false,
    val latestRun: Run? = null,
    val activeRuns: List<Run>? = null,
    val isSending: Boolean = false,
    val latestRunState: RunPresentationState? = null,
    val latestRunRetryAvailable: Boolean = false,
    val activeResponse: SessionMessageUiState? = null,
    val isRefreshing: Boolean = false,
    val messages: List<SessionMessageUiState>? = null,
)

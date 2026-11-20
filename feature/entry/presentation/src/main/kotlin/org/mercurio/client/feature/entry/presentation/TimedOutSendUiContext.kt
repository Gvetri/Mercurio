package org.mercurio.client.feature.entry.presentation

internal data class TimedOutSendUiContext(
    val current: SessionListUiState,
    val opened: OpenSessionUiState,
    val recoveryDraft: PendingDraft? = null,
    val authoritativeMessages: List<SessionMessageUiState>? = null,
)

package org.mercurio.client.feature.entry.presentation

internal data class SessionDetailState(
    val content: OpenSessionUiState,
    val mutation: SessionMutationUiState?,
    val modelPicker: ModelPickerUiState = ModelPickerUiState(),
    val actionsEnabled: Boolean,
    val listRequestActive: Boolean,
    val listIsStale: Boolean,
    val listErrorCategory: SessionListErrorCategory?,
) {
    fun retryEnabled(canSubmit: Boolean): Boolean =
        actionsEnabled &&
            canSubmit &&
            !content.isRefreshing &&
            mutation?.pendingAction == null &&
            !content.isSending &&
            !content.isReconciliationInProgress &&
            !content.hasUnresolvedSubmission &&
            !listRequestActive
}

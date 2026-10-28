package org.mercurio.client.feature.entry.presentation

internal fun EntryStateHolder.requestRemoveGatewayConnection() {
    val state = mutableUiState.value
    if (state.isVerifying || state.isSaving || state.isRemoveConfirmationOpen) return
    if (!state.isGatewayConnectionConfigured && !state.isConnected) return
    mutableUiState.value = state.copy(isRemoveConfirmationOpen = true)
}

internal fun EntryStateHolder.cancelRemoveGatewayConnection() {
    mutableUiState.value = mutableUiState.value.copy(isRemoveConfirmationOpen = false)
}

internal fun EntryStateHolder.confirmRemoveGatewayConnection() {
    mutableUiState.value = mutableUiState.value.copy(isRemoveConfirmationOpen = false)
    removeGatewayConnection()
}

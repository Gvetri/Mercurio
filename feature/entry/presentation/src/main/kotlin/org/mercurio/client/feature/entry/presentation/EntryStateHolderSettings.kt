package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.flow.update

internal fun EntryStateHolder.openSettings() {
    val state = mutableUiState.value
    val sessionList = state.sessionList
    if (sessionList == null || sessionList.createSession != null) return
    mutableUiState.update { current -> current.copy(isSettingsOpen = true) }
}

internal fun EntryStateHolder.closeSettings() {
    mutableUiState.update { current -> current.copy(isSettingsOpen = false) }
}

internal fun EntryStateHolder.toggleAutoLoadImages() {
    val enabled = !mutableUiState.value.autoLoadImages
    imageLoadingSettingsStore?.saveEnabled(enabled)
    mutableUiState.update { current -> current.copy(autoLoadImages = enabled) }
}

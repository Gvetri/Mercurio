package org.hermesnative.client.feature.entry.presentation

import kotlinx.coroutines.flow.update

/** Opens the Settings clear confirmation; the store is touched only by an explicit confirm. */
internal fun EntryStateHolder.requestClearHistoryCache() {
    val state = mutableUiState.value
    if (!state.isSettingsOpen || state.isClearHistoryCacheConfirmationOpen) return
    mutableUiState.update { current -> current.copy(isClearHistoryCacheConfirmationOpen = true) }
}

internal fun EntryStateHolder.cancelClearHistoryCache() {
    mutableUiState.update { current -> current.copy(isClearHistoryCacheConfirmationOpen = false) }
}

/**
 * Empties every endpoint's cached content in one action after an explicit confirm. A
 * successful clear drops cache-served content from the view right away, so the UI keeps
 * claiming nothing that the store no longer holds.
 */
internal fun EntryStateHolder.confirmClearHistoryCache() {
    if (!mutableUiState.value.isClearHistoryCacheConfirmationOpen) return
    val cleared = runCatching { historyCacheStore.clear() }.isSuccess
    mutableUiState.update { current ->
        val reset = if (cleared) current.withCacheServedContentDropped() else current
        reset.copy(isClearHistoryCacheConfirmationOpen = false)
    }
}

/**
 * Drops the rows and the opened transcript that were served from the cache: after the
 * store empties, that content has no backing, and the offline experience must show the
 * truthful cleared state without a restart. Authoritative content is left untouched.
 */
private fun EntryUiState.withCacheServedContentDropped(): EntryUiState {
    val list = sessionList ?: return this
    if (!list.isCached && list.openedSession?.isCached != true) {
        return this
    }
    val clearedList =
        if (list.isCached) {
            list.copy(
                sessions = emptyList(),
                isCached = false,
                isStale = true,
                isUnavailable = true,
            )
        } else {
            list
        }
    val closedTranscript =
        if (clearedList.openedSession?.isCached == true) {
            clearedList.copy(openedSession = null)
        } else {
            clearedList
        }
    return copy(sessionList = closedTranscript)
}

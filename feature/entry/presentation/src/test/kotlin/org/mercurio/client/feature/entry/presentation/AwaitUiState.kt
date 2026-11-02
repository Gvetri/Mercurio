package org.mercurio.client.feature.entry.presentation

import app.cash.turbine.test

internal suspend fun EntryStateHolder.awaitUiState(
    description: String,
    predicate: (EntryUiState) -> Boolean,
): EntryUiState {
    var matched: EntryUiState? = null
    uiState.test {
        var candidate = awaitItem()
        while (!predicate(candidate)) {
            candidate = awaitItem()
        }
        matched = candidate
        cancelAndIgnoreRemainingEvents()
    }
    return matched ?: throw AssertionError("Timed out waiting for $description.")
}

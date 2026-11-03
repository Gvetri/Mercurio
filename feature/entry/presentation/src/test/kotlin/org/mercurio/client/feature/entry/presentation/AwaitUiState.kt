package org.mercurio.client.feature.entry.presentation

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

internal suspend fun EntryStateHolder.awaitUiState(
    description: String,
    predicate: (EntryUiState) -> Boolean,
): EntryUiState {
    var matched: EntryUiState? = null
    try {
        uiState.test(timeout = ASYNC_TEST_TIMEOUT_MILLIS.milliseconds) {
            var candidate = awaitItem()
            while (!predicate(candidate)) {
                candidate = awaitItem()
            }
            matched = candidate
            cancelAndIgnoreRemainingEvents()
        }
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw AssertionError("Timed out waiting for $description. Current state: ${uiState.value}", error)
    }
    return requireNotNull(matched) { "The flow completed while waiting for $description." }
}

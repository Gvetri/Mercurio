package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface EntryStateHolderCommand {
    class Event(
        val event: EntryUiEvent,
    ) : EntryStateHolderCommand

    class StateChange(
        val change: () -> Unit,
    ) : EntryStateHolderCommand

    data object Close : EntryStateHolderCommand
}

private val activeConsumer = ThreadLocal<EntryStateHolder?>()

internal fun EntryStateHolder.startCommandConsumer() {
    holderConsumerJob =
        scope.launch(commandDispatcher) {
            for (command in holderCommandChannel) {
                when (command) {
                    is EntryStateHolderCommand.Event -> consume { handleEntryEvent(command.event) }
                    is EntryStateHolderCommand.StateChange -> consume { command.change() }
                    EntryStateHolderCommand.Close -> {
                        teardownHolder()
                        holderCommandChannel.close()
                        break
                    }
                }
            }
        }
}

internal fun EntryStateHolder.enqueueEvent(event: EntryUiEvent) {
    holderCommandChannel.trySend(EntryStateHolderCommand.Event(event))
}

internal fun EntryStateHolder.enqueueClose() {
    holderCommandChannel.trySend(EntryStateHolderCommand.Close)
}

internal suspend fun <T> EntryStateHolder.onHolderState(change: () -> T): T {
    if (activeConsumer.get() === this) return change()
    val outcome = CompletableDeferred<Result<T>>()
    holderCommandChannel.send(EntryStateHolderCommand.StateChange { outcome.complete(runCatching(change)) })
    return outcome.await().getOrThrow()
}

private inline fun EntryStateHolder.consume(block: () -> Unit) {
    activeConsumer.set(this)
    try {
        runCatching(block).onFailure { error ->
            if (error is CancellationException) throw error
            errorReporting?.reportUnexpectedFailure()
        }
    } finally {
        activeConsumer.remove()
    }
}

internal suspend fun EntryStateHolder.teardownHolder() {
    withContext(NonCancellable) {
        mutableUiState.value = mutableUiState.value.copy(isRemoveConfirmationOpen = false)
        val released = releaseConnectionState()
        released.jobs.forEach { job -> job.cancel() }
        released.observations.forEach { observation -> observation.close() }
        persistPendingCreatesOffThread(released.pendingCreates)
        scope.cancel()
    }
}

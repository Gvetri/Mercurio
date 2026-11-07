package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class StateChangeConsumerMarker :
    AbstractCoroutineContextElement(StateChangeConsumerMarker) {
    companion object Key : CoroutineContext.Key<StateChangeConsumerMarker>
}

internal class StateChangeCommand(
    internal val apply: () -> Any?,
) {
    internal val done = CompletableDeferred<Any?>()
}

internal suspend fun <T> EntryStateHolder.applyStateChange(block: () -> T): T {
    if (currentCoroutineContext()[StateChangeConsumerMarker] != null) {
        return block()
    }
    val command = StateChangeCommand(block)
    stateChangeCommands.send(command)
    @Suppress("UNCHECKED_CAST")
    return command.done.await() as T
}

/**
 * Applies a UI-state write on the state-change consumer without suspending the caller.
 *
 * The write is enqueued behind every already-submitted command, so producers that cannot
 * suspend (job `finally` blocks, cancellation barriers) still serialize their visible state
 * through the same consumer without adding a coroutine to the producer dispatcher. A holder
 * scope that dies on `close` leaves the command unapplied, which matches the close contract:
 * no UI is visible anymore.
 *
 * Every block must re-derive its guards from current state, because the consumer applies it
 * after an unbounded number of earlier commands have run; a block that also touches the
 * request bookkeeping maps must take `synchronized(sessionRequestLock)`.
 */
internal fun EntryStateHolder.submitStateChange(block: () -> Unit) {
    val command = StateChangeCommand(block)
    check(stateChangeCommands.trySend(command).isSuccess) { "State-change queue refused a submission." }
}

internal suspend fun EntryStateHolder.consumeStateChanges() {
    for (command in stateChangeCommands) {
        runCatching { command.apply() }.fold(
            onSuccess = { command.done.complete(it) },
            onFailure = { command.done.completeExceptionally(it) },
        )
    }
}

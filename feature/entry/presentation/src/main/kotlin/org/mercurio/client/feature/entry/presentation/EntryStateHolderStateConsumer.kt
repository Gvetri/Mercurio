package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext

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
 * through the same consumer without adding a coroutine to the producer dispatcher. Close
 * enqueues the holder teardown behind the commands already queued, so a command submitted
 * before `close` is still applied. A command submitted after the teardown has stopped the
 * consumer leaves the command unapplied, which matches the close contract: no UI is visible
 * anymore.
 *
 * Every block must re-derive its guards from current state, because the consumer applies it
 * after an unbounded number of earlier commands have run.
 *
 * The queue is unbounded, so a refused enqueue only happens once the holder's scope has
 * stopped draining it; this keeps `submitStateChange` safe to call from producer `finally`
 * blocks without masking the failure the block is unwinding.
 */
internal fun EntryStateHolder.submitStateChange(block: () -> Unit) {
    val command = StateChangeCommand(block)
    stateChangeCommands.trySend(command)
}

internal suspend fun EntryStateHolder.consumeStateChanges() {
    for (command in stateChangeCommands) {
        runCatching { command.apply() }.fold(
            onSuccess = { command.done.complete(it) },
            onFailure = { command.done.completeExceptionally(it) },
        )
    }
}

/**
 * Suspends until every state-change command enqueued before this call has been applied by the
 * state-change consumer. Commands are applied FIFO, so awaiting the trailing marker guarantees
 * the caller observes the UI state produced by all earlier enqueued writes.
 */
internal suspend fun EntryStateHolder.awaitStateChangesApplied() {
    val applied = CompletableDeferred<Unit>()
    submitStateChange {
        applied.complete(Unit)
    }
    applied.await()
}

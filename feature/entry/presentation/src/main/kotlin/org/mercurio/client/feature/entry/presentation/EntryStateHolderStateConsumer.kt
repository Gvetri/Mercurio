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

internal suspend fun EntryStateHolder.consumeStateChanges() {
    for (command in stateChangeCommands) {
        runCatching { command.apply() }.fold(
            onSuccess = { command.done.complete(it) },
            onFailure = { command.done.completeExceptionally(it) },
        )
    }
}

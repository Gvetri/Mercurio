package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CompletableDeferred

internal class StateChangeCommand(
    internal val apply: () -> Any?,
) {
    internal val done = CompletableDeferred<Any?>()
}

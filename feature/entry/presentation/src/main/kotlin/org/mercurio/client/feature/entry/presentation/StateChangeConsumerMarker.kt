package org.mercurio.client.feature.entry.presentation

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class StateChangeConsumerMarker : AbstractCoroutineContextElement(StateChangeConsumerMarker) {
    companion object Key : CoroutineContext.Key<StateChangeConsumerMarker>
}

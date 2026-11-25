package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runBlocking
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation

internal fun RunEventObservation.collectEvents(): List<RunEvent> =
    runBlocking {
        buildList {
            while (true) {
                val event = next() ?: break
                add(event)
            }
        }
    }

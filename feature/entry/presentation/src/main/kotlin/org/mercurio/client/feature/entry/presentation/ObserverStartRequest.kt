package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Run

internal data class ObserverStartRequest(
    val run: Run,
    val connectionGeneration: Long,
    val sessionGeneration: Long,
)

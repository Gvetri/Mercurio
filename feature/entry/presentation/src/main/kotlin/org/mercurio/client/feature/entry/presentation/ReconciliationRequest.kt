package org.mercurio.client.feature.entry.presentation

internal data class ReconciliationRequest(
    val connectionGeneration: Long,
    val sessionGeneration: Long,
)

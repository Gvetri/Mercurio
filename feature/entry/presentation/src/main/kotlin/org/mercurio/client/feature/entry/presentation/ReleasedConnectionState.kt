package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunEventObservation

internal data class ReleasedConnectionState(
    val jobs: List<Job>,
    val observations: List<RunEventObservation>,
    val pendingCreates: List<PendingCreatePersistence> = emptyList(),
)

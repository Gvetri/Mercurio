package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunEventObservation

internal data class SubmittedRunApplicationResult(
    val applied: Boolean,
    val persistAfterDisconnect: Boolean = false,
    val requestSessionGeneration: Long = 0L,
    val shouldObserve: Boolean = false,
    val observationJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
)

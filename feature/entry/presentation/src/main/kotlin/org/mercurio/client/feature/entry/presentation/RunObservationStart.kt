package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunEventObservation

internal data class RunObservationStart(
    val jobToStart: Job? = null,
    val observerJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
    val handoffRequested: Boolean = false,
)

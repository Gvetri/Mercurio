package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunEventObservation

internal data class ReconciledObservationRelease(
    val job: Job? = null,
    val observation: RunEventObservation? = null,
    val wasActivelyObserved: Boolean = false,
)

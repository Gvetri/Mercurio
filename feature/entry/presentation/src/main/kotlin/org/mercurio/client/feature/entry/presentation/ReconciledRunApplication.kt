package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry

internal data class ReconciledRunApplication(
    val observationJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
    val recoveryEntryToRemove: RunRecoveryEntry? = null,
    val recoveryEndpointToRemove: String? = null,
)

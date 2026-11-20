package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId

internal data class PendingCreateState(
    val knownRunIds: Set<RunId>,
    val attemptId: String,
)

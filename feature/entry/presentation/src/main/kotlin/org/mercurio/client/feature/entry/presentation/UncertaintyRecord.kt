package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot

internal data class UncertaintyRecord(
    val attemptId: String,
    val knownRunIds: Set<RunId> = emptySet(),
    val boundRunId: RunId? = null,
    val settled: Boolean = false,
    val requiresRunMatch: Boolean = false,
)

internal fun UncertaintyRecord.toSnapshot(): RunSubmissionUncertaintySnapshot =
    RunSubmissionUncertaintySnapshot(
        attemptId = attemptId,
        knownRunIds = knownRunIds,
        boundRunId = boundRunId,
        settled = settled,
        requiresRunMatch = requiresRunMatch,
    )

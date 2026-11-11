package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId

internal data class UncertaintyRecord(
    val attemptId: String,
    val knownRunIds: MutableSet<RunId> = mutableSetOf(),
    var boundRunId: RunId? = null,
    var settled: Boolean = false,
    var requiresRunMatch: Boolean = false,
)

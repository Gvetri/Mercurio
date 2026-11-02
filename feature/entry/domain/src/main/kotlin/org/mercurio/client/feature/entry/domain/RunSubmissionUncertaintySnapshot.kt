package org.mercurio.client.feature.entry.domain

data class RunSubmissionUncertaintySnapshot(
    val attemptId: String,
    val knownRunIds: Set<RunId>,
    val boundRunId: RunId?,
    val settled: Boolean,
    val requiresRunMatch: Boolean,
)

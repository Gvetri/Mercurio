package org.mercurio.client.feature.entry.domain

data class SessionReconciliation(
    val history: SessionHistory,
    val discoveredRuns: List<Run>,
)

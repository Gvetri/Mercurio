package org.mercurio.client.feature.entry.domain

/** Minimum metadata needed to query a locally started Run after process restart. */
data class RunRecoveryEntry(
    val sessionId: SessionId,
    val runId: RunId,
)

fun RunRecoveryEntry.isValid(): Boolean = sessionId.value.isNotBlank() && runId.value.isNotBlank()

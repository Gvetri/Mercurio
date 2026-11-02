package org.mercurio.client.feature.entry.domain

data class PendingRunSubmissionKey(
    val endpoint: String,
    val sessionId: SessionId,
)

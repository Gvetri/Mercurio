package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.SessionId

internal data class SubmittedRunApplication(
    val sessionId: SessionId,
    val run: Run,
    val requestConnectionGeneration: Long,
    val requestEndpoint: String,
    val attemptId: String,
    val pendingKey: PendingRunSubmissionKey,
    val submissionBindingSucceeded: Boolean,
    val recoveryPersistenceFailed: Boolean,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey

internal data class SubmittedRunPreparation(
    val pendingKey: PendingRunSubmissionKey,
    val submissionBindingSucceeded: Boolean,
    val persistBeforeApply: Boolean,
    val recoveryPersistenceFailed: Boolean,
)

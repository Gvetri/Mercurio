package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry

internal data class TimedOutSendReconciliationOutcome(
    val applied: Boolean,
    val runToObserve: Run? = null,
    val runToPersist: RunRecoveryEntry? = null,
    val runToRemove: RunRecoveryEntry? = null,
    val ambiguousMark: Pair<PendingRunSubmissionKey, String>? = null,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId

internal data class TimedOutSendReconciliationRequest(
    val sessionId: SessionId,
    val connectionGeneration: Long,
    val knownRunIds: Set<RunId>,
    val attemptId: String,
    val resolveWhenNoNewRun: Boolean = false,
    val uncertaintyErrorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
)

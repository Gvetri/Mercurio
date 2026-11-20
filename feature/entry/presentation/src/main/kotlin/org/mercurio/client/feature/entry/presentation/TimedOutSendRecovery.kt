package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId

internal data class TimedOutSendRecovery(
    val knownRunIds: Set<RunId>,
    val attemptId: String,
    val draft: PendingDraft,
    val errorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
)

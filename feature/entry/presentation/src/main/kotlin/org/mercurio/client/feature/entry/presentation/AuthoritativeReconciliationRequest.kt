package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionId

internal data class AuthoritativeReconciliationRequest(
    val sessionId: SessionId,
    val connectionGeneration: Long,
    val sessionGeneration: Long?,
    val historyGeneration: Long? = null,
    val updateVisibleUi: Boolean = true,
)

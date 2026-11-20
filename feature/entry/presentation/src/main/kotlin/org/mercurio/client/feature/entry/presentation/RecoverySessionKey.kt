package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionId

internal data class RecoverySessionKey(
    val endpoint: String,
    val sessionId: SessionId,
)

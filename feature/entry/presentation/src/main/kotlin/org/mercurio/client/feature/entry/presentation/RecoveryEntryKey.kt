package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId

internal data class RecoveryEntryKey(
    val endpoint: String,
    val sessionId: SessionId,
    val runId: RunId,
)

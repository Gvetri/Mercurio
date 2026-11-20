package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId

internal data class RunSubmissionWork(
    val gateway: RunGatewayPort,
    val input: String,
    val rejectRunId: RunId?,
    val sessionId: SessionId,
    val requestConnectionGeneration: Long,
    val requestEndpoint: String,
    val recoverySessionKey: RecoverySessionKey,
    val pendingCreate: PendingCreateState,
    val attemptId: String,
    val knownRunIds: Set<RunId>,
    val selection: ModelSelection?,
    val recordDraft: Boolean,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId

internal data class TimedOutSendAttempt(
    val sessionId: SessionId,
    val gateways: Pair<SessionGatewayPort, RunGatewayPort>,
    val knownRunIds: Set<RunId>,
    val requestEndpoint: String,
    val request: ReconciliationRequest,
    val resolveWhenNoNewRun: Boolean,
)

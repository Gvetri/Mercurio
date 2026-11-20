package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.SessionGatewayPort

internal data class RecoveryGatewayContext(
    val endpoint: String,
    val sessionGateway: SessionGatewayPort,
    val runGateway: RunGatewayPort,
)

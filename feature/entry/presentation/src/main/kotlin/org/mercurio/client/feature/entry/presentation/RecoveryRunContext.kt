package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.SessionGatewayPort

internal data class RecoveryRunContext(
    val sessionGateway: SessionGatewayPort,
    val runGateway: RunGatewayPort,
    val connectionGeneration: Long,
    val historyGeneration: Long,
    val sessionGeneration: Long? = null,
    val updateVisibleUi: Boolean = false,
)

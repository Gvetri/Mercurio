package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayCapabilityPort

class VerifyGatewayCapabilities(
    private val gateway: GatewayCapabilityPort,
) {
    fun execute(): GatewayCapabilities = gateway.discoverCapabilities()
}

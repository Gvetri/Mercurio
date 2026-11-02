package org.mercurio.client.feature.entry.domain

interface GatewayCapabilityPort {
    fun discoverCapabilities(): GatewayCapabilities
}

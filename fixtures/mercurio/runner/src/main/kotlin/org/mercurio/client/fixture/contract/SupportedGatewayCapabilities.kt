package org.mercurio.client.fixture.contract

import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest

object SupportedGatewayCapabilities {
    val required = PublicBetaGatewayCapabilityManifest.current.requirements.map { it.endpoint }.distinct()
}

package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.GatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.GatewayContractPort
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest

class DefaultGatewayClient(
    endpoint: String,
    bearerToken: String,
    transport: GatewayTransport = OkHttpGatewayTransport(),
    manifest: GatewayCapabilityManifest = PublicBetaGatewayCapabilityManifest.current,
) : GatewayContractPort by GatewayClientOperations(
        calls = GatewayClientCalls(endpoint, bearerToken, transport),
        manifest = manifest,
    )

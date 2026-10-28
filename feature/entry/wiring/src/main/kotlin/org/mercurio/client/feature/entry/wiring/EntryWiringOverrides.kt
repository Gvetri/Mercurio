package org.mercurio.client.feature.entry.wiring

import kotlinx.coroutines.CoroutineScope
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayCredentialStore
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.SessionGatewayPort

data class EntryWiringOverrides(
    val capabilityDiscovery: ((String, String) -> GatewayCapabilities)? = null,
    val sessionGatewayFactory: ((String, String) -> SessionGatewayPort)? = null,
    val runGatewayFactory: ((String, String) -> RunGatewayPort)? = null,
    val coroutineScope: CoroutineScope? = null,
    val credentialStore: GatewayCredentialStore? = null,
    val historyCacheStore: HistoryCacheStore? = null,
)

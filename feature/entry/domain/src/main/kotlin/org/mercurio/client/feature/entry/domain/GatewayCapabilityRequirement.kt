package org.mercurio.client.feature.entry.domain

data class GatewayCapabilityRequirement(
    val endpoint: String,
    val method: String,
    val path: String,
    val operation: GatewayContractOperation,
)

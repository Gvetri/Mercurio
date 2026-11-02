package org.mercurio.client.feature.entry.domain

data class GatewayCapabilityManifest(
    val version: Int,
    val discoveryPath: String,
    val requirements: List<GatewayCapabilityRequirement>,
) {
    /** Required endpoint name to the pinned method and path the client calls. */
    val requiredEndpoints: Map<String, GatewayEndpoint>
        get() = requirements.associate { it.endpoint to GatewayEndpoint(it.method, it.path) }
}

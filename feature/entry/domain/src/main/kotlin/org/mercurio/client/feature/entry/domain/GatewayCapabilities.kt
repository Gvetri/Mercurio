package org.mercurio.client.feature.entry.domain

/**
 * Fail-closed check of a discovered `endpoints` table against the pinned manifest.
 *
 * A required endpoint must be present with the exact pinned method and path: the
 * client's requests are hardcoded to those paths, so a name-only or method-only
 * match could accept a Gateway whose routes the client cannot call.
 */
fun GatewayCapabilities.satisfies(manifest: GatewayCapabilityManifest): Boolean =
    manifest.requiredEndpoints.all { (name, required) ->
        endpoints[name].matches(required)
    }

private fun GatewayEndpoint?.matches(required: GatewayEndpoint): Boolean =
    this != null &&
        method.equals(required.method, ignoreCase = true) &&
        path == required.path

data class GatewayCapabilities(
    val endpoints: Map<String, GatewayEndpoint>,
) {
    fun supports(endpoint: String): Boolean = endpoint in endpoints
}

/**
 * Additive-optional detection of the Run model catalog.
 *
 * The catalog is available only when the discovered table advertises the pinned
 * `run_models` row with the exact method and path; absence or mismatch means the
 * catalog is unavailable, never that the connection fails.
 */
fun GatewayCapabilities.supportsModelCatalog(): Boolean {
    val required = PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint
    return supports(required.endpoint) &&
        endpoints[required.endpoint].matches(GatewayEndpoint(required.method, required.path))
}

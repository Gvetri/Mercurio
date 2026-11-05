package org.mercurio.client.feature.entry.domain

/**
 * Discovers and fetches the additive-optional Run model catalog for the current endpoint.
 *
 * The catalog follows the documented fail-closed ladder: callers fetch only for a surface the
 * discovered capabilities advertise ([modelCatalogSurface]), and they pass that surface here so
 * the request calls the exact advertised row. A transport or non-200 failure surfaces as
 * `GATEWAY_REQUEST_FAILED` and a malformed response as `INVALID_RESPONSE`. Both mean "catalog
 * unavailable, selection hidden" and never block connection verification. Ports that do not
 * implement the catalog fail closed instead of returning an empty catalog.
 */
interface ModelGatewayPort {
    fun fetchModelCatalog(surface: ModelCatalogSurface): ModelCatalog =
        throw GatewayException(
            GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
            "Model catalog unavailable on this Gateway port.",
        )
}

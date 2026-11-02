package org.mercurio.client.feature.entry.domain

/**
 * Discovers and fetches the additive-optional Run model catalog for the current endpoint.
 *
 * The catalog follows the documented fail-closed ladder: callers fetch only when the
 * discovered capabilities advertise the `run_models` row ([supportsModelCatalog]); a
 * transport or non-200 failure surfaces as `GATEWAY_REQUEST_FAILED` and a malformed
 * response as `INVALID_RESPONSE`. Both mean "catalog unavailable, selection hidden" and
 * never block connection verification. Catalog availability is exposed by
 * [GatewayCapabilities.supportsModelCatalog] on the discovered endpoints table; callers consult it and
 * never fetch when it is false. Ports that do not implement the catalog fail
 * closed instead of returning an empty catalog.
 */
interface ModelGatewayPort {
    fun fetchModelCatalog(): ModelCatalog =
        throw GatewayException(
            GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
            "Model catalog unavailable on this Gateway port.",
        )
}

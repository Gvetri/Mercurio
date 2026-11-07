package org.mercurio.client.feature.entry.domain

/**
 * An additive-optional Gateway surface that can carry the Run model catalog.
 *
 * Each surface pairs the endpoint row a Gateway advertises with the exact method and path the
 * client calls, so an accepted row always resolves to the route the request uses. A surface is
 * never a client requirement: a Gateway without one keeps its connection healthy and offers no
 * model selection.
 */
enum class ModelCatalogSurface(
    val endpoint: String,
    val method: String,
    val path: String,
) {
    /** The pinned catalog contract. No deployed Hermes revision serves this row yet. */
    RUN_MODELS("run_models", "GET", "/api/models"),

    /** The deployed provider-grouped inventory that a live Hermes Gateway advertises today. */
    MODEL_OPTIONS("model_options", "GET", "/api/model/options"),
}

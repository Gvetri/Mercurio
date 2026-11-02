package org.mercurio.client.feature.entry.domain

/**
 * Domain types for the additive-optional Run model catalog (`GET /api/models`).
 *
 * The catalog vocabulary is server-owned: model IDs, display names, providers, and
 * effort values come only from the Gateway response and are never hardcoded here.
 */
data class ModelCatalog(
    /**
     * The `object` discriminator of the catalog response. The pinned contract value is
     * `hermes.api_server.run_models`; the parser validates it and fails closed on a
     * mismatch before this type is constructed.
     */
    val discriminator: String,
    /** The server's configured Run model; null means only the generic "Server default" baseline is known. */
    val defaultModel: String?,
    /** The catalog entries in server order; the parser guarantees a non-empty list. */
    val entries: List<ModelCatalogEntry>,
)

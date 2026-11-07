package org.mercurio.client.feature.entry.domain

/**
 * Domain type for the additive-optional Run model catalog.
 *
 * The catalog vocabulary is server-owned: model IDs, display names, providers, and
 * effort values come only from the Gateway response and are never hardcoded here.
 */
data class ModelCatalog(
    /** The advertised surface that produced [entries]; the request called this surface's row. */
    val surface: ModelCatalogSurface,
    /** The server's configured Run model; null means only the generic "Server default" baseline is known. */
    val defaultModel: String?,
    /** The catalog entries in server order; the parser guarantees a non-empty list. */
    val entries: List<ModelCatalogEntry>,
)

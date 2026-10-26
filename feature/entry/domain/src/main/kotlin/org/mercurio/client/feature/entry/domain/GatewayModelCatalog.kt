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

/**
 * One selectable model. [id] is the exact value the client sends as the Run-creation
 * `model` key; [displayName] is UI text and [provider] is informational only, never
 * credential material. A null [effort] means the server declares no effort control for
 * this model.
 */
data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val provider: String?,
    val effort: ModelEffortOptions?,
)

/**
 * The effort control a catalog entry may carry. [supported] is a non-empty, ordered
 * list of server-owned values; [default] is a member of [supported] or null.
 */
data class ModelEffortOptions(
    val supported: List<String>,
    val default: String?,
)

/**
 * A per-endpoint Run model override. The combined model-and-effort control keeps the
 * invariant that an effort is only ever carried with its model: a selection without a
 * model cannot be constructed, and no selection means the Server default is used with
 * no Run-creation selection keys.
 */
data class ModelSelection(
    val model: String,
    val effort: String? = null,
)

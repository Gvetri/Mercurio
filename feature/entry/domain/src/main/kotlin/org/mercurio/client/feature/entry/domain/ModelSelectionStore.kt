package org.mercurio.client.feature.entry.domain

/**
 * Persists the per-endpoint Run model selection, favorite model IDs, and recently used
 * model IDs in the same tier as the composer draft store. Reads return the last stored
 * state for the addressed endpoint only; a cleared override returns to the server
 * default, which sends no Run-creation selection keys.
 */
interface ModelSelectionStore {
    /** The last override for the endpoint, or null when no override is set. */
    fun read(endpoint: String): ModelSelection?

    fun write(
        endpoint: String,
        selection: ModelSelection,
    )

    /** Removes the override so submissions use the server default again. */
    fun clear(endpoint: String)

    /** Favorite model IDs for the endpoint; ordering is a presentation concern. */
    fun favorites(endpoint: String): Set<String>

    fun setFavorite(
        endpoint: String,
        modelId: String,
        favorite: Boolean,
    )

    /** Recently used model IDs, most-recent-first, bounded, without duplicates. */
    fun recents(endpoint: String): List<String>

    /** Records a use so the model moves to the front of the recents list. */
    fun recordUse(
        endpoint: String,
        modelId: String,
    )

    /** Purges every record this store holds for the endpoint (connection removal). */
    fun clearEndpoint(endpoint: String) = Unit
}

/** In-memory default used when no persisted selection store is provided. */
object NoOpModelSelectionStore : ModelSelectionStore {
    override fun read(endpoint: String): ModelSelection? = null

    override fun write(
        endpoint: String,
        selection: ModelSelection,
    ) = Unit

    override fun clear(endpoint: String) = Unit

    override fun favorites(endpoint: String): Set<String> = emptySet()

    override fun setFavorite(
        endpoint: String,
        modelId: String,
        favorite: Boolean,
    ) = Unit

    override fun recents(endpoint: String): List<String> = emptyList()

    override fun recordUse(
        endpoint: String,
        modelId: String,
    ) = Unit
}

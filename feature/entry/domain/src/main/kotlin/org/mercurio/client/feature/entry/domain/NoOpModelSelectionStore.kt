package org.mercurio.client.feature.entry.domain

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

package org.mercurio.client.feature.entry.domain

/** In-memory default used when no persisted draft store is provided. */
object NoOpSessionDraftStore : SessionDraftStore {
    override fun read(key: SessionDraftKey): String? = null

    override fun write(
        key: SessionDraftKey,
        text: String,
    ) = Unit

    override fun remove(key: SessionDraftKey) = Unit
}

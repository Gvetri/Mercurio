package org.mercurio.client.feature.entry.domain

/** In-memory default used when no persisted history cache store is provided. */
object NoOpHistoryCacheStore : HistoryCacheStore {
    override fun read(key: HistoryCacheKey): List<GatewayHistoryMessage> = emptyList()

    override fun write(
        key: HistoryCacheKey,
        messages: List<GatewayHistoryMessage>,
    ) = Unit

    override fun readSessionList(endpoint: String): List<Session> = emptyList()

    override fun writeSessionList(
        endpoint: String,
        sessions: List<Session>,
    ) = Unit

    override fun clearEndpoint(endpoint: String) = Unit

    override fun clear() = Unit
}

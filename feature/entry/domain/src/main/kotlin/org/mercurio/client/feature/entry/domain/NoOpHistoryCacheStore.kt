package org.mercurio.client.feature.entry.domain

/** In-memory default used when no persisted history cache store is provided. */
object NoOpHistoryCacheStore : HistoryCacheStore {
    override fun read(key: HistoryCacheKey): List<GatewayHistoryMessage> = emptyList()

    override suspend fun write(
        key: HistoryCacheKey,
        messages: List<GatewayHistoryMessage>,
    ) = Unit

    override fun readSessionList(endpoint: String): List<Session> = emptyList()

    override suspend fun writeSessionList(
        endpoint: String,
        sessions: List<Session>,
    ) = Unit

    override suspend fun clearEndpoint(endpoint: String) = Unit

    override suspend fun clear() = Unit
}

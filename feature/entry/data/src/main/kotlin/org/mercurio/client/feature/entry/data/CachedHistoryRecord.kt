package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.Session
import java.time.Instant

/** One cached entry with its storage key and the instant it entered the cache. */
data class CachedHistoryRecord(
    val key: String,
    val message: GatewayHistoryMessage? = null,
    val cachedAt: Instant,
    val sessionList: List<Session> = emptyList(),
)

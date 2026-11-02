package org.mercurio.client.feature.entry.domain

/** The Gateway endpoint and Session whose cached transcript is addressed. */
data class HistoryCacheKey(
    val endpoint: String,
    val sessionId: SessionId,
)

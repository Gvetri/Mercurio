package org.mercurio.client.feature.entry.domain

/** The Gateway endpoint and Session a persisted composer draft belongs to. */
data class SessionDraftKey(
    val endpoint: String,
    val sessionId: SessionId,
)

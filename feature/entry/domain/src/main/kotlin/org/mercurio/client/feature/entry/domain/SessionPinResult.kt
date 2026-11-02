package org.mercurio.client.feature.entry.domain

data class SessionPinResult(
    val sessionId: SessionId,
    val pinned: Boolean,
)

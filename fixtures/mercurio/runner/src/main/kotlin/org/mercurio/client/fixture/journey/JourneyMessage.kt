package org.mercurio.client.fixture.journey

/** A pinned Session message projection has no run identity metadata. */
data class JourneyMessage(
    val id: String,
    val role: String?,
    val content: String?,
    val timestamp: String? = null,
)

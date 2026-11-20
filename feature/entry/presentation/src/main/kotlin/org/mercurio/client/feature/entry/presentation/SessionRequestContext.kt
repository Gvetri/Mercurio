package org.mercurio.client.feature.entry.presentation

internal data class SessionRequestContext(
    val generation: Long,
    val query: String,
    val offset: Int?,
    val linked: Boolean = false,
)

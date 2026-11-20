package org.mercurio.client.feature.entry.presentation

internal data class CreateSessionRequest(
    val context: SessionRequestContext,
    val title: String?,
)

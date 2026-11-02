package org.mercurio.client.feature.entry.application

const val UNKNOWN_CLIENT_VERSION = "unknown"

data class EntryState(
    val isGatewayConnectionConfigured: Boolean = false,
    val configuredEndpoint: String? = null,
    val configuredCredential: String? = null,
    val clientVersion: String = UNKNOWN_CLIENT_VERSION,
)

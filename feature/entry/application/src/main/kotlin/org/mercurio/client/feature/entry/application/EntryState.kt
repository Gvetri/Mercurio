package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.InstallSource

const val UNKNOWN_CLIENT_VERSION = "unknown"

data class EntryState(
    val isGatewayConnectionConfigured: Boolean = false,
    val configuredEndpoint: String? = null,
    val configuredCredential: String? = null,
    val clientVersion: String = UNKNOWN_CLIENT_VERSION,
    val buildNumber: Long = 0L,
    val installSource: InstallSource = InstallSource.OTHER_SOURCE,
)

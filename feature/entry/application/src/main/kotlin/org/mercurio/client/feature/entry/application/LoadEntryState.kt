package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

const val UNKNOWN_CLIENT_VERSION = "unknown"

data class EntryState(
    val isGatewayConnectionConfigured: Boolean = false,
    val configuredEndpoint: String? = null,
    val configuredCredential: String? = null,
    val clientVersion: String = UNKNOWN_CLIENT_VERSION,
)

class LoadEntryState(
    private val gatewayConnectionRepository: GatewayConnectionRepository,
    private val installedBuildProvider: InstalledBuildProvider,
) {
    fun execute(): EntryState {
        val connection = gatewayConnectionRepository.load()
        val configuredEndpoint = connection?.endpoint
        return EntryState(
            isGatewayConnectionConfigured = configuredEndpoint != null,
            configuredEndpoint = configuredEndpoint,
            configuredCredential = connection?.bearerCredential,
            clientVersion = installedBuildProvider.installedBuild().versionNameOr(UNKNOWN_CLIENT_VERSION),
        )
    }
}

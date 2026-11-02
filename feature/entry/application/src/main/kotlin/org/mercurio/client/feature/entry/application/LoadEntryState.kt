package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

class LoadEntryState(
    private val gatewayConnectionRepository: GatewayConnectionRepository,
    private val installedBuildProvider: InstalledBuildProvider,
) {
    fun execute(): EntryState {
        val connection = gatewayConnectionRepository.load()
        val configuredEndpoint = connection?.endpoint
        val installedBuild = installedBuildProvider.installedBuild()
        return EntryState(
            isGatewayConnectionConfigured = configuredEndpoint != null,
            configuredEndpoint = configuredEndpoint,
            configuredCredential = connection?.bearerCredential,
            clientVersion = installedBuild.versionNameOr(UNKNOWN_CLIENT_VERSION),
            buildNumber = installedBuild.versionCode,
            installSource = installedBuild.installSource,
        )
    }
}

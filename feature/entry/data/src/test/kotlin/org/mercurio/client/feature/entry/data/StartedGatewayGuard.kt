package org.mercurio.client.feature.entry.data

import java.net.URI

internal object StartedGatewayGuard {
    private val loopbackHosts = setOf("127.0.0.1", "localhost", "::1")

    fun requireStartedLoopbackEndpoint(
        fixtureContextEndpoint: URI,
        startedProcessEndpoint: URI,
    ) {
        require(startedProcessEndpoint.scheme == "https") {
            "The lane only drives the https endpoint of the Gateway process it started, " +
                "not scheme ${startedProcessEndpoint.scheme}."
        }
        require(startedProcessEndpoint.host in loopbackHosts) {
            "The started Gateway process must be on loopback, not host ${startedProcessEndpoint.host}."
        }
        require(fixtureContextEndpoint.host in loopbackHosts) {
            "The fixture context must point at loopback, not host ${fixtureContextEndpoint.host}."
        }
        require(fixtureContextEndpoint.port == startedProcessEndpoint.port) {
            "The fixture context port ${fixtureContextEndpoint.port} does not match " +
                "the started process port ${startedProcessEndpoint.port}."
        }
    }

    fun requireConfiguredEndpoint(
        configuredEndpoint: URI,
        clientEndpoint: URI,
    ) {
        require(clientEndpoint.scheme == "https") {
            "The environment-configured lane only drives https endpoints, not scheme ${clientEndpoint.scheme}."
        }
        require(clientEndpoint == configuredEndpoint) {
            "The lane must drive the configured environment Gateway endpoint $configuredEndpoint, " +
                "not $clientEndpoint."
        }
    }
}

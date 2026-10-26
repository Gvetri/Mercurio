package org.mercurio.client.feature.entry.application

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayCapabilityPort

class VerifyGatewayCapabilitiesTest {
    @Test
    fun application_consumes_the_typed_capability_port() {
        val expected =
            GatewayCapabilities(
                mapOf(
                    "sessions" to
                        org.mercurio.client.feature.entry.domain.GatewayEndpoint("GET", "/api/sessions"),
                ),
            )

        val result = VerifyGatewayCapabilities(FakeCapabilityPort(expected)).execute()

        assertEquals(expected, result)
    }

    private class FakeCapabilityPort(
        private val capabilities: GatewayCapabilities,
    ) : GatewayCapabilityPort {
        override fun discoverCapabilities(): GatewayCapabilities = capabilities
    }
}

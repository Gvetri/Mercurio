package org.hermesnative.client.feature.entry.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class LiveGatewayEnvironmentRunTest {
    @After
    fun clearLaneEnvironmentProperties() {
        System.clearProperty(LIVE_GATEWAY_ENDPOINT_PROPERTY)
        System.clearProperty(LIVE_GATEWAY_BEARER_TOKEN_PROPERTY)
    }

    @Test
    fun lane_environment_properties_supply_the_external_endpoint_and_credential() {
        assertNull(laneGatewayEndpoint())
        assertNull(laneGatewayBearerToken())

        System.setProperty(LIVE_GATEWAY_ENDPOINT_PROPERTY, "https://gateway.example.internal")
        System.setProperty(LIVE_GATEWAY_BEARER_TOKEN_PROPERTY, "environment-bearer")

        assertEquals(URI("https://gateway.example.internal"), laneGatewayEndpoint())
        assertEquals("environment-bearer", laneGatewayBearerToken())
    }

    @Test
    fun environment_run_drives_the_configured_endpoint_without_starting_a_local_process() {
        val configuredEndpoint = URI("https://gateway.example.internal")
        val run = environmentRun(configuredEndpoint)

        assertEquals(configuredEndpoint, run.endpoint)
        StartedGatewayGuard.requireConfiguredEndpoint(configuredEndpoint, run.endpoint)
        assertTrue(run.parityCaptures.delegate is OkHttpGatewayTransport)
        laneClient(run, "environment-bearer")
    }

    @Test
    fun environment_run_guard_rejects_any_endpoint_other_than_the_configured_one() {
        val configuredEndpoint = URI("https://gateway.example.internal")

        val insecureFailure =
            assertThrows(IllegalArgumentException::class.java) {
                StartedGatewayGuard.requireConfiguredEndpoint(
                    configuredEndpoint,
                    URI("http://gateway.example.internal"),
                )
            }
        assertTrue(insecureFailure.message.orEmpty().contains("https"))

        val foreignFailure =
            assertThrows(IllegalArgumentException::class.java) {
                StartedGatewayGuard.requireConfiguredEndpoint(configuredEndpoint, URI("https://other-gateway.example"))
            }
        assertTrue(foreignFailure.message.orEmpty().contains("configured environment Gateway endpoint"))
    }
}

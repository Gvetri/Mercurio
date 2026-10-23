package org.hermesnative.client.feature.entry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCapabilityManifestTest {
    @Test
    fun public_beta_manifest_is_versioned_and_maps_every_required_endpoint() {
        val manifest = PublicBetaGatewayCapabilityManifest.current

        assertEquals(1, manifest.version)
        assertEquals("/v1/capabilities", manifest.discoveryPath)
        assertEquals(
            listOf(
                "sessions",
                "session_create",
                "session",
                "session_messages",
                "session_update",
                "session_delete",
                "session_update",
                "session_update",
                "runs",
                "run_status",
                "run_events",
            ),
            manifest.requirements.map { it.endpoint },
        )
        assertEquals(
            listOf(
                "GET",
                "POST",
                "GET",
                "GET",
                "PATCH",
                "DELETE",
                "PATCH",
                "PATCH",
                "POST",
                "GET",
                "GET",
            ),
            manifest.requirements.map { it.method },
        )
        assertEquals(
            listOf(
                "/api/sessions",
                "/api/sessions",
                "/api/sessions/{session_id}",
                "/api/sessions/{session_id}/messages",
                "/api/sessions/{session_id}",
                "/api/sessions/{session_id}",
                "/api/sessions/{session_id}",
                "/api/sessions/{session_id}",
                "/v1/runs",
                "/v1/runs/{run_id}",
                "/v1/runs/{run_id}/events",
            ),
            manifest.requirements.map { it.path },
        )
        assertRequiredEndpointMap(manifest)
    }

    private fun assertRequiredEndpointMap(manifest: GatewayCapabilityManifest) {
        assertEquals(manifest.requirements.map { it.endpoint }.distinct().size, manifest.requiredEndpoints.size)
        assertEquals(GatewayEndpoint("GET", "/api/sessions"), manifest.requiredEndpoints.getValue("sessions"))
        assertEquals(
            GatewayEndpoint("PATCH", "/api/sessions/{session_id}"),
            manifest.requiredEndpoints.getValue("session_update"),
        )
        assertEquals(GatewayEndpoint("POST", "/v1/runs"), manifest.requiredEndpoints.getValue("runs"))
        assertEquals(
            GatewayEndpoint("GET", "/v1/runs/{run_id}/events"),
            manifest.requiredEndpoints.getValue("run_events"),
        )
        assertSame(GatewayContractOperation.SESSION_LIST, manifest.requirements.first().operation)
        assertSame(GatewayContractOperation.RUN_SSE, manifest.requirements.last().operation)
    }

    @Test
    fun satisfies_requires_every_pinned_name_method_and_path_fail_closed() {
        val manifest = PublicBetaGatewayCapabilityManifest.current
        val pinned = manifest.requiredEndpoints

        assertTrue(GatewayCapabilities(pinned).satisfies(manifest))

        val wrongPath =
            pinned.toMutableMap().apply { this["sessions"] = GatewayEndpoint("GET", "/v1/sessions") }
        assertFalse(GatewayCapabilities(wrongPath).satisfies(manifest))

        val trailingSlashPath =
            pinned.toMutableMap().apply { this["sessions"] = GatewayEndpoint("GET", "/api/sessions/") }
        assertFalse(GatewayCapabilities(trailingSlashPath).satisfies(manifest))

        val wrongMethod =
            pinned.toMutableMap().apply { this["runs"] = GatewayEndpoint("GET", "/v1/runs") }
        assertFalse(GatewayCapabilities(wrongMethod).satisfies(manifest))

        val missingEndpoint = pinned.toMutableMap().apply { remove("run_events") }
        assertFalse(GatewayCapabilities(missingEndpoint).satisfies(manifest))

        val extraEndpoint =
            pinned + ("gateway_future_endpoint" to GatewayEndpoint("GET", "/v1/future"))
        assertTrue(GatewayCapabilities(extraEndpoint).satisfies(manifest))

        val upperCaseMethod =
            pinned.toMutableMap().apply { this["sessions"] = GatewayEndpoint("get", "/api/sessions") }
        assertTrue(GatewayCapabilities(upperCaseMethod).satisfies(manifest))
    }

    @Test
    fun model_catalog_detection_requires_the_exact_pinned_row_and_never_a_requirement() {
        val manifest = PublicBetaGatewayCapabilityManifest.current
        val row = PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint

        assertEquals("run_models", row.endpoint)
        assertEquals(GatewayEndpoint("GET", "/api/models"), GatewayEndpoint(row.method, row.path))
        assertSame(GatewayContractOperation.MODEL_CATALOG, row.operation)
        assertTrue(manifest.requirements.none { it.endpoint == row.endpoint })
        assertFalse(manifest.requiredEndpoints.containsKey(row.endpoint))

        listOf(
            GatewayEndpoint("GET", "/api/models") to true,
            GatewayEndpoint("get", "/api/models") to true,
            GatewayEndpoint("POST", "/api/models") to false,
            GatewayEndpoint("GET", "/v1/models") to false,
            GatewayEndpoint("GET", "/api/models/") to false,
        ).forEach { (advertised, expected) ->
            assertEquals(
                "row advertised as $advertised",
                expected,
                catalogCapabilities(advertised).supportsModelCatalog(),
            )
        }

        assertTrue(catalogCapabilities(GatewayEndpoint("GET", "/api/models")).satisfies(manifest))
        assertFalse(GatewayCapabilities(emptyMap()).supportsModelCatalog())
        assertFalse(
            GatewayCapabilities(mapOf("models" to GatewayEndpoint("GET", "/api/models")))
                .supportsModelCatalog(),
        )
    }

    private fun catalogCapabilities(catalogEndpoint: GatewayEndpoint): GatewayCapabilities =
        GatewayCapabilities(
            PublicBetaGatewayCapabilityManifest.current.requiredEndpoints +
                (PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint.endpoint to catalogEndpoint),
        )
}

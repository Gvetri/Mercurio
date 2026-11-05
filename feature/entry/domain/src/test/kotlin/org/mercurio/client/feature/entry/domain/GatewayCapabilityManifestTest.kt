package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun model_catalog_detection_accepts_only_an_exact_advertised_surface() {
        val surfaces = PublicBetaGatewayCapabilityManifest.modelCatalogSurfaces
        assertEquals(
            listOf(ModelCatalogSurface.RUN_MODELS, ModelCatalogSurface.MODEL_OPTIONS),
            surfaces,
        )
        assertTrue(
            surfaces.none { surface ->
                PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.containsKey(surface.endpoint)
            },
        )
        assertEquals(
            listOf(
                GatewayEndpoint("GET", "/api/models"),
                GatewayEndpoint("GET", "/api/model/options"),
            ),
            surfaces.map { GatewayEndpoint(it.method, it.path) },
        )

        listOf(
            ModelCatalogSurface.RUN_MODELS to
                listOf(
                    GatewayEndpoint("GET", "/api/models") to true,
                    GatewayEndpoint("get", "/api/models") to true,
                    GatewayEndpoint("POST", "/api/models") to false,
                    GatewayEndpoint("GET", "/api/models/") to false,
                ),
            ModelCatalogSurface.MODEL_OPTIONS to
                listOf(
                    GatewayEndpoint("GET", "/api/model/options") to true,
                    GatewayEndpoint("get", "/api/model/options") to true,
                    GatewayEndpoint("POST", "/api/model/options") to false,
                    GatewayEndpoint("GET", "/api/model/options/") to false,
                ),
        ).forEach { (surface, cases) ->
            cases.forEach { (advertised, accepted) ->
                assertEquals(
                    "surface $surface advertised as $advertised",
                    if (accepted) surface else null,
                    capabilitiesWith(surface to advertised).modelCatalogSurface(),
                )
            }
        }

        assertNull(GatewayCapabilities(emptyMap()).modelCatalogSurface())
        assertNull(
            GatewayCapabilities(mapOf("models" to GatewayEndpoint("GET", "/api/models")))
                .modelCatalogSurface(),
        )
    }

    @Test
    fun a_gateway_that_advertises_both_catalog_surfaces_resolves_the_pinned_one_first() {
        val capabilities =
            capabilitiesWith(
                ModelCatalogSurface.RUN_MODELS to GatewayEndpoint("GET", "/api/models"),
                ModelCatalogSurface.MODEL_OPTIONS to GatewayEndpoint("GET", "/api/model/options"),
            )

        assertSame(ModelCatalogSurface.RUN_MODELS, capabilities.modelCatalogSurface())
        assertTrue(capabilities.satisfies(PublicBetaGatewayCapabilityManifest.current))
    }

    private fun capabilitiesWith(vararg advertised: Pair<ModelCatalogSurface, GatewayEndpoint>): GatewayCapabilities =
        GatewayCapabilities(
            PublicBetaGatewayCapabilityManifest.current.requiredEndpoints +
                advertised.map { (surface, endpoint) -> surface.endpoint to endpoint },
        )
}

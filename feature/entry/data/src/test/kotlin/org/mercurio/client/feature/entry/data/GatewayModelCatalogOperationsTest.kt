package org.mercurio.client.feature.entry.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.ModelCatalog
import org.mercurio.client.feature.entry.domain.ModelCatalogEntry
import org.mercurio.client.feature.entry.domain.ModelCatalogSurface
import org.mercurio.client.feature.entry.domain.ModelEffortOptions
import java.io.File
import java.net.URI
import java.util.ArrayDeque

class GatewayModelCatalogOperationsTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )
    private val contractsRoot = repositoryRoot.resolve("fixtures/mercurio/contracts")

    @Test
    fun catalog_fetch_uses_the_pinned_request_and_parses_the_pinned_success_fixture() {
        val transport = RecordingTransport(responses = listOf(response("models/success.json")))

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)

        assertFixtureCatalogRequest(transport.requests.single())
        assertEquals(ModelCatalogSurface.RUN_MODELS, catalog.surface)
        assertEquals("deepseek-v4-flash", catalog.defaultModel)
        assertEquals(listOf("deepseek-v4-flash", "hermes-agent"), catalog.entries.map { it.id })
        val first = catalog.entries.first()
        assertEquals("DeepSeek V4 Flash", first.displayName)
        assertEquals("opencode-go", first.provider)
        assertEquals(listOf("low", "medium", "high"), first.effort?.supported)
        assertEquals("medium", first.effort?.default)
        val second = catalog.entries.last()
        assertNull(second.provider)
        assertNull(second.effort)
    }

    @Test
    fun catalog_allows_an_explicit_null_default_model() {
        val body = JsonObject(successBody() + ("default_model" to JsonNull)).toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)

        assertEquals(2, catalog.entries.size)
        assertNull(catalog.defaultModel)
    }

    @Test
    fun catalog_tolerates_unknown_additive_fields_at_every_level() {
        val transport = RecordingTransport(responses = listOf(response("models/unknown-additive.json")))

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)

        assertEquals(PINNED_CATALOG, catalog)
    }

    @Test
    fun a_catalog_missing_the_models_array_fails_closed_as_an_invalid_response() {
        val transport = RecordingTransport(responses = listOf(response("models/missing-required.json")))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
        }
    }

    @Test
    fun an_empty_models_array_fails_closed_as_an_invalid_response() {
        val body = JsonObject(successBody() + ("models" to JsonArray(emptyList()))).toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
        }
    }

    @Test
    fun a_foreign_object_discriminator_fails_closed_as_an_invalid_response() {
        val body = successBody().toString().replace(CATALOG_OBJECT_TOKEN, "\"object\":\"list\"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
        }
    }

    @Test
    fun an_effort_default_outside_the_supported_vocabulary_fails_closed() {
        val body = successBody().toString().replace("\"default\":\"medium\"", "\"default\":\"turbo\"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
        }
    }

    @Test
    fun a_blank_model_entry_identity_fails_closed() {
        val body = successBody().toString().replace("\"id\":\"deepseek-v4-flash\"", "\"id\":\" \"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
        }
    }

    @Test
    fun a_non_200_catalog_response_fails_with_the_request_category_not_the_connection() {
        val transport =
            RecordingTransport(
                responses = listOf(GatewayHttpResponse(404, "{\"error\":{\"message\":\"no route\"}}")),
            )

        val error = captureFailure { client(transport).fetchModelCatalog(ModelCatalogSurface.RUN_MODELS) }

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, error.category)
    }

    @Test
    fun the_deployed_options_surface_calls_its_advertised_row_and_maps_the_captured_inventory() {
        val transport = RecordingTransport(responses = listOf(response("models/options-success.json")))

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)

        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("/api/model/options", URI(request.url).path)
        assertEquals("Bearer test-token", request.headers["Authorization"])
        assertNull(request.body)
        assertEquals(ModelCatalogSurface.MODEL_OPTIONS, catalog.surface)
        assertEquals("step-5-preview", catalog.defaultModel)
        assertEquals(DEPLOYED_ENTRY_IDS, catalog.entries.map { it.id })
        val first = catalog.entries.first()
        assertEquals("step-5-preview", first.displayName)
        assertEquals("opencode-go", first.provider)
        assertNull(first.effort)
    }

    @Test
    fun an_unauthenticated_provider_never_contributes_selectable_entries() {
        val body = deployedBody().toString().replaceFirst("\"authenticated\":true", "\"authenticated\":false")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)

        assertEquals(DEPLOYED_ENTRY_IDS.drop(4), catalog.entries.map { it.id })
        assertTrue(catalog.entries.none { it.provider == "opencode-go" })
    }

    @Test
    fun a_model_id_advertised_by_two_providers_appears_once_in_server_order() {
        val body = deployedBody()
        val providers =
            JsonArray(
                body.getValue("providers").jsonArray.map { element ->
                    val provider = element.jsonObject
                    if (provider.getValue("slug").jsonPrimitive.content != "nous") {
                        element
                    } else {
                        JsonObject(
                            provider +
                                (
                                    "models" to
                                        JsonArray(
                                            provider.getValue("models").jsonArray + JsonPrimitive("step-5-preview"),
                                        )
                                ),
                        )
                    }
                },
            )
        val transport =
            RecordingTransport(
                responses = listOf(GatewayHttpResponse(200, JsonObject(body + ("providers" to providers)).toString())),
            )

        val catalog = client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)

        assertEquals(DEPLOYED_ENTRY_IDS, catalog.entries.map { it.id })
        assertEquals(1, catalog.entries.count { it.id == "step-5-preview" })
        assertEquals("opencode-go", catalog.entries.first().provider)
    }

    @Test
    fun an_inventory_with_no_authenticated_model_fails_closed_as_an_invalid_response() {
        val body = deployedBody().toString().replace("\"authenticated\":true", "\"authenticated\":false")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)
        }
    }

    @Test
    fun a_deployed_inventory_without_the_providers_array_fails_closed() {
        val body = JsonObject(deployedBody() - "providers").toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)
        }
    }

    @Test
    fun a_malformed_deployed_provider_row_fails_closed() {
        val body =
            JsonObject(
                deployedBody() + ("providers" to JsonArray(listOf(JsonPrimitive("not-a-provider")))),
            ).toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)
        }
    }

    @Test
    fun a_blank_deployed_model_id_fails_closed() {
        val body = deployedBody()
        val providers =
            JsonArray(
                body.getValue("providers").jsonArray.mapIndexed { index, element ->
                    if (index != 0) {
                        element
                    } else {
                        JsonObject(element.jsonObject + ("models" to JsonArray(listOf(JsonPrimitive(" ")))))
                    }
                },
            )
        val mutated = JsonObject(body + ("providers" to providers)).toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, mutated)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) {
            client(transport).fetchModelCatalog(ModelCatalogSurface.MODEL_OPTIONS)
        }
    }

    private fun client(transport: RecordingTransport) =
        DefaultGatewayClient(
            endpoint = "https://gateway.example",
            bearerToken = "test-token",
            transport = transport,
        )

    private fun successBody(): JsonObject =
        Json
            .parseToJsonElement(contractsRoot.resolve("models/success.json").readText())
            .jsonObject
            .getValue("response")
            .jsonObject
            .getValue("body")
            .jsonObject

    private fun deployedBody(): JsonObject =
        Json
            .parseToJsonElement(contractsRoot.resolve("models/options-success.json").readText())
            .jsonObject
            .getValue("response")
            .jsonObject
            .getValue("body")
            .jsonObject

    private fun assertFixtureCatalogRequest(request: GatewayHttpRequest) {
        val root = Json.parseToJsonElement(contractsRoot.resolve("models/request.json").readText()).jsonObject
        val expected = root.getValue("request").jsonObject
        assertEquals(expected.getValue("method").jsonPrimitive.content, request.method)
        assertEquals(expected.getValue("path").jsonPrimitive.content, URI(request.url).path)
        assertEquals("Bearer test-token", request.headers["Authorization"])
        assertNull(request.body)
    }

    private fun response(path: String): GatewayHttpResponse {
        val root = Json.parseToJsonElement(contractsRoot.resolve(path).readText()).jsonObject
        val response = root.getValue("response").jsonObject
        return GatewayHttpResponse(
            statusCode = response.getValue("status").jsonPrimitive.content.toInt(),
            body = response.getValue("body").toString(),
        )
    }

    private fun captureFailure(block: () -> Unit): GatewayException {
        try {
            block()
        } catch (error: GatewayException) {
            return error
        }
        throw AssertionError("Expected GatewayException")
    }

    private fun assertFailure(
        category: GatewayErrorCategory,
        block: () -> Unit,
    ) {
        assertEquals(category, captureFailure(block).category)
    }

    private companion object {
        const val CATALOG_OBJECT_TOKEN = "\"object\":\"hermes.api_server.run_models\""

        val DEPLOYED_ENTRY_IDS =
            listOf(
                "step-5-preview",
                "qwen3.8-max",
                "qwen3.8-flash",
                "longcat-2.0",
                "deepseek/deepseek-v4-pro",
                "deepseek/deepseek-v4-flash",
                "Qwen/Qwen3.7-Max",
                "Qwen/Qwen3.6-Plus",
                "anthropic/claude-fable-5.1",
                "anthropic/claude-fable-5",
                "anthropic/claude-opus-5.5",
                "anthropic/claude-opus-5",
            )

        val PINNED_CATALOG =
            ModelCatalog(
                surface = ModelCatalogSurface.RUN_MODELS,
                defaultModel = "deepseek-v4-flash",
                entries =
                    listOf(
                        ModelCatalogEntry(
                            id = "deepseek-v4-flash",
                            displayName = "DeepSeek V4 Flash",
                            provider = "opencode-go",
                            effort =
                                ModelEffortOptions(
                                    supported = listOf("low", "medium", "high"),
                                    default = "medium",
                                ),
                        ),
                        ModelCatalogEntry(
                            id = "hermes-agent",
                            displayName = "Hermes Agent",
                            provider = null,
                            effort = null,
                        ),
                    ),
            )
    }

    private class RecordingTransport(
        responses: List<GatewayHttpResponse> = emptyList(),
    ) : GatewayTransport {
        private val queuedResponses = ArrayDeque(responses)
        val requests = mutableListOf<GatewayHttpRequest>()

        override fun execute(request: GatewayHttpRequest): GatewayHttpResponse {
            requests += request
            return queuedResponses.removeFirst()
        }

        override fun openEventStream(request: GatewayHttpRequest): GatewayEventStream {
            requests += request
            error("The model catalog never opens an event stream.")
        }
    }
}

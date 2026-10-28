package org.mercurio.client.feature.entry.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.ModelCatalog
import org.mercurio.client.feature.entry.domain.ModelCatalogEntry
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

        val catalog = client(transport).fetchModelCatalog()

        assertFixtureCatalogRequest(transport.requests.single())
        assertEquals("hermes.api_server.run_models", catalog.discriminator)
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

        val catalog = client(transport).fetchModelCatalog()

        assertEquals(2, catalog.entries.size)
        assertNull(catalog.defaultModel)
    }

    @Test
    fun catalog_tolerates_unknown_additive_fields_at_every_level() {
        val transport = RecordingTransport(responses = listOf(response("models/unknown-additive.json")))

        val catalog = client(transport).fetchModelCatalog()

        assertEquals(PINNED_CATALOG, catalog)
    }

    @Test
    fun a_catalog_missing_the_models_array_fails_closed_as_an_invalid_response() {
        val transport = RecordingTransport(responses = listOf(response("models/missing-required.json")))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) { client(transport).fetchModelCatalog() }
    }

    @Test
    fun an_empty_models_array_fails_closed_as_an_invalid_response() {
        val body = JsonObject(successBody() + ("models" to JsonArray(emptyList()))).toString()
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) { client(transport).fetchModelCatalog() }
    }

    @Test
    fun a_foreign_object_discriminator_fails_closed_as_an_invalid_response() {
        val body = successBody().toString().replace(CATALOG_OBJECT_TOKEN, "\"object\":\"list\"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) { client(transport).fetchModelCatalog() }
    }

    @Test
    fun an_effort_default_outside_the_supported_vocabulary_fails_closed() {
        val body = successBody().toString().replace("\"default\":\"medium\"", "\"default\":\"turbo\"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) { client(transport).fetchModelCatalog() }
    }

    @Test
    fun a_blank_model_entry_identity_fails_closed() {
        val body = successBody().toString().replace("\"id\":\"deepseek-v4-flash\"", "\"id\":\" \"")
        val transport = RecordingTransport(responses = listOf(GatewayHttpResponse(200, body)))

        assertFailure(GatewayErrorCategory.INVALID_RESPONSE) { client(transport).fetchModelCatalog() }
    }

    @Test
    fun a_non_200_catalog_response_fails_with_the_request_category_not_the_connection() {
        val transport =
            RecordingTransport(
                responses = listOf(GatewayHttpResponse(404, "{\"error\":{\"message\":\"no route\"}}")),
            )

        val error = captureFailure { client(transport).fetchModelCatalog() }

        assertEquals(GatewayErrorCategory.GATEWAY_REQUEST_FAILED, error.category)
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

        val PINNED_CATALOG =
            ModelCatalog(
                discriminator = "hermes.api_server.run_models",
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

package org.mercurio.client.fixture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

internal object HttpFixtureReadinessChecker : FixtureReadinessChecker {
    override fun verify(
        process: GatewayProcess,
        descriptor: PinnedFixtureDescriptor,
    ) {
        require(process.isRunning) { "Deterministic Gateway process is not running." }
        val health = get(process.endpoint.resolve(descriptor.value("health_check.path")))
        requireStatus(
            checkName = "health",
            response = health,
            expectedStatus = descriptor.value("health_check.expected_status").toInt(),
        )

        val capabilities = get(process.endpoint.resolve(descriptor.value("capability_check.path")))
        requireStatus(
            checkName = "capability",
            response = capabilities,
            expectedStatus = descriptor.value("capability_check.expected_status").toInt(),
        )
        val manifestKey = descriptor.value("capability_check.required_capabilities")
        require(manifestKey == CLIENT_MANIFEST) {
            "Unsupported capability manifest check '$manifestKey'."
        }
        val advertised =
            try {
                val root = Json.parseToJsonElement(capabilities.body).jsonObject
                root["endpoints"]?.jsonObject ?: error("capability document has no 'endpoints' object")
            } catch (error: IllegalArgumentException) {
                throw FixtureReadinessException(
                    "Capability check response is not a valid capability document.",
                    error,
                )
            } catch (error: IllegalStateException) {
                throw FixtureReadinessException(
                    "Capability check response is not a valid capability document.",
                    error,
                )
            }
        PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.forEach { (endpoint, required) ->
            val advertisedEndpoint = advertised[endpoint]?.jsonObject
            val advertisedMethod = advertisedEndpoint?.get("method")?.jsonPrimitive?.content
            val advertisedPath = advertisedEndpoint?.get("path")?.jsonPrimitive?.content
            require(advertisedMethod?.equals(required.method, ignoreCase = true) == true) {
                "Capability check did not report required capability '$endpoint'."
            }
            require(advertisedPath == required.path) {
                "Capability check reported the wrong path for required capability '$endpoint'."
            }
        }
    }

    private fun get(uri: URI): HttpResponse {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = READINESS_TIMEOUT_MILLIS
        connection.readTimeout = READINESS_TIMEOUT_MILLIS
        return try {
            val status = connection.responseCode
            val stream =
                if (status in HTTP_SUCCESS_FIRST..HTTP_SUCCESS_LAST) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } catch (error: IOException) {
            throw FixtureReadinessException("Readiness request to $uri failed.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireStatus(
        checkName: String,
        response: HttpResponse,
        expectedStatus: Int,
    ) {
        require(response.status == expectedStatus) {
            "$checkName check expected HTTP $expectedStatus but received ${response.status}."
        }
    }

    private data class HttpResponse(
        val status: Int,
        val body: String,
    )
}

private const val READINESS_TIMEOUT_MILLIS = 1_000

private const val HTTP_SUCCESS_FIRST = 200

private const val HTTP_SUCCESS_LAST = 399

private const val CLIENT_MANIFEST = "client-manifest"

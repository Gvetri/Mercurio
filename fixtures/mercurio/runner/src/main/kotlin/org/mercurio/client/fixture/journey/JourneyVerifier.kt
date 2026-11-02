package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

fun main(args: Array<String>) {
    require(args.size in MIN_ARGUMENT_COUNT..MAX_ARGUMENT_COUNT) {
        "Usage: JourneyVerifier <telemetry-url> <scenario-name> [keystore-file] [keystore-password]"
    }
    val telemetryUrl = URI.create(args[0])
    val scenarioName = args[1]
    val keystoreFile = args.getOrNull(2)?.let(::File)
    val keystorePassword = args.getOrNull(KEYSTORE_PASSWORD_ARG_INDEX) ?: DEFAULT_KEYSTORE_PASSWORD
    val telemetry = fetchTelemetry(telemetryUrl, keystoreFile, keystorePassword)
    JourneyInvariants.verify(scenarioName, telemetry)
    if (scenarioName == "active-run-isolation") {
        val run =
            fetchTelemetry(
                telemetryUrl.resolve("/v1/runs/${JourneyInvariants.ACTIVE_RUN_ID}"),
                keystoreFile,
                keystorePassword,
            )
        check(run["status"]?.jsonPrimitive?.content == "running") {
            "The active Run did not remain running after the Session switch."
        }
    }
    println("journey-verifier=pass")
}

internal fun fetchTelemetry(
    telemetryUrl: URI,
    keystoreFile: File? = null,
    keystorePassword: String = DEFAULT_KEYSTORE_PASSWORD,
): JsonObject {
    val connection =
        if (telemetryUrl.scheme == "https") {
            val keyStore =
                KeyStore.getInstance("PKCS12").apply {
                    requireNotNull(keystoreFile).inputStream()
                        .use { input -> load(input, keystorePassword.toCharArray()) }
                }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)
            val sslContext =
                SSLContext.getInstance("TLS").apply {
                    init(null, trustManagerFactory.trustManagers, null)
                }
            (telemetryUrl.toURL().openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = sslContext.socketFactory
            }
        } else {
            telemetryUrl.toURL().openConnection() as HttpURLConnection
        }
    connection.connectTimeout = TELEMETRY_TIMEOUT_MILLIS
    connection.readTimeout = TELEMETRY_TIMEOUT_MILLIS
    connection.requestMethod = "GET"
    connection.setRequestProperty("X-Journey-Probe", "verifier")
    if (connection is HttpsURLConnection) {
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    }
    return try {
        check(connection.responseCode == HTTP_OK) { "Telemetry endpoint returned HTTP ${connection.responseCode}." }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(body).jsonObject
    } finally {
        connection.disconnect()
    }
}

internal fun stringOrNull(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive
    return primitive?.takeUnless { it is JsonNull }?.content
}

internal fun requireConnectionAttempted(telemetry: JsonObject) {
    val requests = telemetry["requests"]?.jsonArray.orEmpty()
    check(requests.isNotEmpty()) { "The journey Gateway recorded no application requests." }
    val paths = recordedRequestPaths(telemetry)
    check(paths.any { it.startsWith("/v1/") }) {
        "The application never issued a Gateway API request."
    }
}

internal fun recordedRequestPaths(telemetry: JsonObject): List<String> =
    telemetry["requests"]?.jsonArray.orEmpty().mapNotNull { request ->
        request.jsonObject["path"]?.jsonPrimitive?.content
    }

private const val IDLE_GAP_FAST_RUN_ID = "run-fast-first"

private const val IDLE_GAP_SLOW_RUN_ID = "run-idle-gap"

internal fun verifyIdleGapStreaming(telemetry: JsonObject) {
    requireConnectionAttempted(telemetry)
    check(runCreatesFor(telemetry, JourneyInvariants.ACTIVE_SESSION_ID) == 2) {
        "The idle-gap journey did not submit exactly two Runs (fast first message, paced second message)."
    }
    check(sseFor(telemetry, IDLE_GAP_FAST_RUN_ID).opened == 1) {
        "The fast first Run was not observed exactly once."
    }
    check(sseFor(telemetry, IDLE_GAP_SLOW_RUN_ID).opened == 1) {
        "The paced idle-gap Run was not observed exactly once."
    }
    check(runStatusRequests(telemetry) >= 1) {
        "The client did not reconcile the paced Run status."
    }
}

private const val MIN_ARGUMENT_COUNT = 2

private const val MAX_ARGUMENT_COUNT = 4

private const val KEYSTORE_PASSWORD_ARG_INDEX = 3

private const val TELEMETRY_TIMEOUT_MILLIS = 5_000

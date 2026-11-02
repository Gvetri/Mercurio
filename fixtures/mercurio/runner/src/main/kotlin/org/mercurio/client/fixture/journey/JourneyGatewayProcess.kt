package org.mercurio.client.fixture.journey

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mercurio.client.fixture.GatewayHttpSupport
import org.mercurio.client.fixture.GatewayProcess
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * The controlled fake Gateway for deterministic emulator journeys.
 *
 * Starts from an explicit repository-owned [JourneyScenario], serves synthetic
 * session and Run data over HTTP or TLS, exposes deterministic SSE observation,
 * records request/observation telemetry, and verifies clean teardown. It never
 * contacts a public endpoint and uses no real provider credentials.
 *
 * The served shapes follow the pinned Hermes revision: `/v1/capabilities` advertises
 * an `endpoints` table, session resources live under `/api/sessions`, Run admission is
 * `POST /v1/runs`, and Run SSE frames carry the event type inside the JSON `data:`
 * payload without a named `event:` line.
 */
class JourneyGatewayProcess private constructor(
    private val server: HttpServer,
    private val executor: ExecutorService,
    override val endpoint: URI,
    override val provenanceValue: String,
) : GatewayProcess {
    @Volatile
    private var serverStopped = false

    @Volatile
    override var isRunning: Boolean = true
        private set

    override fun stop() {
        if (!isRunning) {
            return
        }
        if (!serverStopped) {
            server.stop(0)
            serverStopped = true
        }
        executor.shutdownNow()
        check(executor.awaitTermination(1, TimeUnit.SECONDS)) {
            "Journey Gateway executor did not stop."
        }
        isRunning = false
    }

    companion object {
        fun start(
            scenario: JourneyScenario,
            keystoreFile: File? = null,
            keystorePassword: String = DEFAULT_KEYSTORE_PASSWORD,
        ): JourneyGatewayProcess {
            val server: HttpServer
            val executor = Executors.newCachedThreadPool()
            if (scenario.tls) {
                val https = HttpsServer.create(InetSocketAddress("127.0.0.1", scenario.port), 0)
                val keyStore =
                    loadKeyStore(
                        requireNotNull(keystoreFile) { "Journey TLS scenarios require a keystore." },
                        keystorePassword,
                    )
                https.httpsConfigurator = HttpsConfigurator(serverSslContext(keyStore, keystorePassword))
                https.executor = executor
                server = https
            } else {
                val http = HttpServer.create(InetSocketAddress("127.0.0.1", scenario.port), 0)
                http.executor = executor
                server = http
            }
            val behavior = JourneyGatewayBehavior(scenario)
            registerRoutes(server, scenario, behavior)
            server.start()
            val scheme = if (scenario.tls) "https" else "http"
            return JourneyGatewayProcess(
                server = server,
                executor = executor,
                endpoint = URI.create("$scheme://127.0.0.1:${server.address.port}"),
                provenanceValue = scenario.hermesRevision,
            )
        }

        private fun registerRoutes(
            server: HttpServer,
            scenario: JourneyScenario,
            behavior: JourneyGatewayBehavior,
        ) {
            server.createContext("/health") { exchange ->
                record(exchange, behavior)
                respond(exchange, HTTP_OK, """{"status":"synthetic"}""")
            }
            server.createContext("/__fixture/telemetry") { exchange ->
                record(exchange, behavior)
                respond(exchange, HTTP_OK, behavior.telemetryJson())
            }
            server.createContext("/__fixture/error-report") { exchange ->
                recordErrorReports(exchange, behavior)
            }
            server.createContext("/v1/capabilities") { exchange ->
                record(exchange, behavior)
                if (unauthorized(exchange, behavior)) {
                    respond(exchange, HTTP_UNAUTHORIZED, """{"error":"unauthorized"}""")
                } else {
                    respond(exchange, HTTP_OK, JourneyGatewayJson.capabilitiesJson(scenario))
                }
            }
            server.createContext("/api/sessions") { exchange ->
                JourneyGatewayRoutes.handleSessionList(exchange, behavior)
            }
            server.createContext("/api/sessions/") { exchange ->
                JourneyGatewayRoutes.handleSessionResource(exchange, behavior)
            }
            if (scenario.modelCatalog != null) {
                server.createContext("/api/models") { exchange ->
                    JourneyGatewayRoutes.handleModelCatalog(exchange, behavior)
                }
            }
            server.createContext("/v1/runs") { exchange ->
                JourneyGatewayRoutes.handleRuns(exchange, behavior)
            }
            server.createContext("/") { exchange ->
                record(exchange, behavior)
                respond(exchange, HTTP_NOT_FOUND, """{"error":"not-found"}""")
            }
        }

        private fun loadKeyStore(
            keystoreFile: File,
            keystorePassword: String,
        ): KeyStore {
            require(keystoreFile.isFile) { "Journey TLS keystore does not exist: ${keystoreFile.absolutePath}" }
            return KeyStore.getInstance("PKCS12").apply {
                keystoreFile.inputStream().use { input ->
                    load(input, keystorePassword.toCharArray())
                }
            }
        }

        private fun serverSslContext(
            keyStore: KeyStore,
            keystorePassword: String,
        ): SSLContext {
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, keystorePassword.toCharArray())
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)
            return SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
            }
        }
    }
}

internal const val HTTP_UNAUTHORIZED = 401

internal const val HTTP_NOT_FOUND = 404

internal const val DEFAULT_KEYSTORE_PASSWORD = "journey-fixture"
internal const val HOLD_OPEN_KEEPALIVE_MILLIS = 500L

internal fun unauthorized(
    exchange: HttpExchange,
    behavior: JourneyGatewayBehavior,
): Boolean {
    val required = behavior.scenario.requireBearerCredential ?: return false
    val authorization = exchange.requestHeaders.getFirst("Authorization")
    return authorization != "Bearer $required"
}

internal fun record(
    exchange: HttpExchange,
    behavior: JourneyGatewayBehavior,
): String? = GatewayHttpSupport.recordRequest(exchange, behavior.requests)

private const val ERROR_REPORT_METHOD = "POST"

private fun recordErrorReports(
    exchange: HttpExchange,
    behavior: JourneyGatewayBehavior,
) {
    if (exchange.requestMethod != ERROR_REPORT_METHOD) {
        GatewayHttpSupport.respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")
        return
    }
    val reports =
        runCatching {
            Json.parseToJsonElement(exchange.requestBody.readBytes().toString(Charsets.UTF_8)).jsonArray
        }.getOrNull()
    if (reports == null) {
        GatewayHttpSupport.respond(exchange, HTTP_BAD_REQUEST, """{"error":"malformed-report"}""")
        return
    }
    reports.forEach { element ->
        val report = element.jsonObject
        behavior.errorReports +=
            report.getValue("release").jsonPrimitive.content to
            report.getValue("environment").jsonPrimitive.content
    }
    GatewayHttpSupport.respond(exchange, HTTP_ACCEPTED, """{"recorded":${reports.size}}""")
}

internal fun respond(
    exchange: HttpExchange,
    status: Int,
    body: String,
) {
    GatewayHttpSupport.respond(exchange, status, body)
}

internal fun String?.jsonValue(): String = GatewayHttpSupport.jsonValue(this)

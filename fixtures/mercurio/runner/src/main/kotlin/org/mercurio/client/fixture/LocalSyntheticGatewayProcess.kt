package org.mercurio.client.fixture

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A loopback-only HTTP process with deterministic health, capability, and synthetic behavior. */
class LocalSyntheticGatewayProcess private constructor(
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
            "Synthetic Gateway executor did not stop."
        }
        isRunning = false
    }

    companion object {
        fun start(
            descriptor: PinnedFixtureDescriptor,
            behavior: SyntheticGatewayBehavior = SyntheticGatewayBehavior(),
        ): LocalSyntheticGatewayProcess =
            start(
                descriptor = descriptor,
                pinnedProvenance = descriptor.provenance,
                behavior = behavior,
            )

        internal fun start(
            descriptor: PinnedFixtureDescriptor,
            pinnedProvenance: PinnedFixtureProvenance,
            behavior: SyntheticGatewayBehavior = SyntheticGatewayBehavior(),
        ): LocalSyntheticGatewayProcess {
            require(descriptor.name == "hermes-deterministic-gateway") {
                "Unsupported deterministic Gateway descriptor '${descriptor.name}'."
            }
            require(pinnedProvenance == descriptor.provenance) {
                "Synthetic Gateway provenance does not match the pinned descriptor."
            }
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val executor = Executors.newSingleThreadExecutor()
            server.executor = executor
            server.createContext("/health") { exchange ->
                recordRequest(exchange, behavior)
                respond(exchange, behavior.healthStatus, "{\"status\":\"synthetic\"}")
            }
            server.createContext("/v1/capabilities") { exchange ->
                recordRequest(exchange, behavior)
                respond(exchange, behavior.capabilityStatus, capabilitiesJson(behavior))
            }
            server.createContext("/api/sessions") { exchange ->
                SyntheticGatewayRoutes.handleSessionListRequest(exchange, behavior)
            }
            server.createContext("/api/sessions/") { exchange ->
                SyntheticGatewayRoutes.handleSessionResourceRequest(exchange, behavior)
            }
            server.createContext("/") { exchange ->
                recordRequest(exchange, behavior)
                respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"not-found\"}")
            }
            server.start()
            return LocalSyntheticGatewayProcess(
                server = server,
                executor = executor,
                endpoint = URI.create("http://127.0.0.1:${server.address.port}"),
                provenanceValue = pinnedProvenance.value,
            )
        }

        private fun capabilitiesJson(behavior: SyntheticGatewayBehavior): String =
            buildString {
                append("{\"object\":\"hermes.api_server.capabilities\",\"platform\":\"hermes-agent\",")
                append("\"model\":\"synthetic\",")
                append("\"auth\":{\"type\":\"bearer\",\"required\":false},")
                append("\"features\":{\"run_submission\":true,\"run_status\":true,\"run_events_sse\":true,")
                append("\"session_resources\":true},")
                append("\"endpoints\":{")
                append(
                    behavior.endpoints.entries.joinToString(",") { (name, endpoint) ->
                        "${quote(name)}:{\"method\":${quote(endpoint.method)},\"path\":${quote(endpoint.path)}}"
                    },
                )
                append("}}")
            }
    }
}

internal const val HTTP_NOT_FOUND = 404

internal fun quote(value: String): String = GatewayHttpSupport.quote(value)

internal fun recordRequest(
    exchange: HttpExchange,
    behavior: SyntheticGatewayBehavior,
): String? = GatewayHttpSupport.recordRequest(exchange, behavior.requests)

internal fun respond(
    exchange: HttpExchange,
    status: Int,
    body: String,
) {
    GatewayHttpSupport.respond(exchange, status, body)
}

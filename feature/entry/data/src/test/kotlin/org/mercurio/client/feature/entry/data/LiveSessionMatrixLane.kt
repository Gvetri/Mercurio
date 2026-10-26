package org.mercurio.client.feature.entry.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.fixture.DeterministicGatewayFixture
import org.mercurio.client.fixture.FixtureReadinessChecker
import org.mercurio.client.fixture.GatewayProcess
import org.mercurio.client.fixture.GatewayProcessFactory
import org.mercurio.client.fixture.PinnedFixtureDescriptor
import org.mercurio.client.fixture.journey.JourneyGatewayProcess
import org.mercurio.client.fixture.journey.JourneyMessage
import org.mercurio.client.fixture.journey.JourneyRunEvent
import org.mercurio.client.fixture.journey.JourneyRunScript
import org.mercurio.client.fixture.journey.JourneyScenario
import org.mercurio.client.fixture.journey.JourneySession
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal const val LIVE_EVIDENCE_DIR_PROPERTY = "live.session.matrix.evidenceDir"
internal const val LIVE_EVIDENCE_DEFAULT_DIR = "build/live-session-matrix-evidence"
internal const val LIVE_GATEWAY_ENDPOINT_PROPERTY = "live.gateway.endpoint"
internal const val LIVE_GATEWAY_BEARER_TOKEN_PROPERTY = "live.gateway.bearerToken"
internal const val LIVE_FIXTURE_FILE_DRIFT_ISSUES_PROPERTY = "live.fixture.fileDriftIssues"
internal const val PINNED_DESCRIPTOR_PATH = "fixtures/mercurio/pinned-fixture.properties"
internal const val JOURNEY_KEYSTORE_PATH = "fixtures/mercurio/journey-tls/journey-gateway.p12"
internal const val JOURNEY_CA_PEM_PATH = "fixtures/mercurio/journey-tls/journey-ca.pem"
internal const val JOURNEY_KEYSTORE_PASSWORD = "journey-fixture"
internal const val SEEDED_PINNED_SESSION_ID = "seeded-pinned-session"
internal const val SEEDED_OPEN_SESSION_ID = "seeded-open-session"
internal const val SEEDED_THIRD_SESSION_ID = "seeded-third-session"
internal const val SEEDED_FIRST_MESSAGE_ID = "seeded-message-1"
internal const val SEEDED_SECOND_MESSAGE_ID = "seeded-message-2"
internal const val SEEDED_FIRST_MESSAGE_CONTENT = "Seeded user message for the live lane"
internal const val SEEDED_SECOND_MESSAGE_CONTENT = "Seeded assistant reply for the live lane"
private const val LANE_SESSION_PAGE_SIZE = 2
private const val JOURNEY_CA_ALIAS = "journey-ca"
private val LANE_ADVERTISED_CAPABILITIES =
    PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.keys + "health"

internal fun freeLoopbackPort(): Int = ServerSocket(0).use { it.localPort }

internal fun evidenceDirectory(): File =
    System.getProperty(LIVE_EVIDENCE_DIR_PROPERTY)
        ?.let(::File)
        ?: File(System.getProperty("user.dir"), LIVE_EVIDENCE_DEFAULT_DIR)

internal fun journeyTrustedClient(journeyCaCertificate: File): OkHttpClient {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val caCertificate = certificateFactory.generateCertificate(journeyCaCertificate.inputStream())
    val trustStore =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry(JOURNEY_CA_ALIAS, caCertificate)
        }
    val trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
    val trustManager = trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val sslContext =
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
    return OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, trustManager).build()
}

internal fun laneScenario(
    descriptor: PinnedFixtureDescriptor,
    port: Int,
    bearerToken: String,
): JourneyScenario =
    JourneyScenario(
        name = "live-session-matrix",
        hermesRevision = descriptor.provenanceValue,
        port = port,
        tls = true,
        capabilities = LANE_ADVERTISED_CAPABILITIES.toList(),
        requireBearerCredential = bearerToken,
        sessionPageSize = LANE_SESSION_PAGE_SIZE,
        sessions =
            listOf(
                JourneySession(
                    id = SEEDED_PINNED_SESSION_ID,
                    title = "Pinned seeded session",
                    preview = "Pinned seeded preview",
                    pinned = true,
                    history =
                        listOf(
                            JourneyMessage(
                                id = SEEDED_FIRST_MESSAGE_ID,
                                role = "user",
                                content = SEEDED_FIRST_MESSAGE_CONTENT,
                                timestamp = "1757325600.0",
                            ),
                            JourneyMessage(
                                id = SEEDED_SECOND_MESSAGE_ID,
                                role = "assistant",
                                content = SEEDED_SECOND_MESSAGE_CONTENT,
                                timestamp = "1757325600.0",
                            ),
                        ),
                ),
                JourneySession(
                    id = SEEDED_OPEN_SESSION_ID,
                    title = "Open seeded session",
                    preview = "Open seeded preview",
                    pinned = false,
                ),
                JourneySession(
                    id = SEEDED_THIRD_SESSION_ID,
                    title = null,
                    preview = null,
                    pinned = false,
                ),
            ),
    )

internal const val RUN_LANE_CREATED_SESSION_ID = "synthetic-created-session-1"
internal const val RUN_LANE_DELTA_RUN_ID = "lane-delta-run"
internal const val RUN_LANE_FRAMING_RUN_ID = "lane-framing-run"
internal const val RUN_LANE_HOLD_OPEN_RUN_ID = "lane-hold-open-run"
internal const val RUN_LANE_UNMAPPED_RUN_ID = "lane-unmapped-run"
internal const val RUN_LANE_REFUSED_RUN_ID = "lane-refused-run"
internal const val RUN_LANE_UNMAPPED_EVENT_TYPE = "metrics.heartbeat"

internal fun runLaneScenario(
    descriptor: PinnedFixtureDescriptor,
    port: Int,
    bearerToken: String,
): JourneyScenario =
    JourneyScenario(
        name = "live-run-matrix",
        hermesRevision = descriptor.provenanceValue,
        port = port,
        tls = true,
        capabilities = LANE_ADVERTISED_CAPABILITIES.toList(),
        requireBearerCredential = bearerToken,
        runs = runLaneScripts(),
    )

internal fun runLaneScripts(): List<JourneyRunScript> =
    listOf(
        JourneyRunScript(
            runId = RUN_LANE_DELTA_RUN_ID,
            sessionId = RUN_LANE_CREATED_SESSION_ID,
            createStatus = "started",
            observation =
                listOf(
                    JourneyRunEvent(type = "message.delta", delta = "Hello "),
                    JourneyRunEvent(type = "message.delta", delta = "from "),
                    JourneyRunEvent(type = "message.delta", delta = "the lane."),
                    JourneyRunEvent(type = "run.completed"),
                ),
            finalStatus = "completed",
        ),
        JourneyRunScript(
            runId = RUN_LANE_FRAMING_RUN_ID,
            sessionId = RUN_LANE_CREATED_SESSION_ID,
            createStatus = "started",
            liveFraming = true,
            observation =
                listOf(
                    JourneyRunEvent(type = "message.delta", delta = "Framed delta"),
                    JourneyRunEvent(type = "run.completed"),
                ),
            finalStatus = "completed",
        ),
        JourneyRunScript(
            runId = RUN_LANE_HOLD_OPEN_RUN_ID,
            sessionId = RUN_LANE_CREATED_SESSION_ID,
            createStatus = "started",
            holdOpen = true,
            finalizeOnDisconnect = true,
            finalStatus = "completed",
            observation =
                listOf(
                    JourneyRunEvent(type = "message.delta", delta = "Held delta"),
                    JourneyRunEvent(type = "run.completed"),
                ),
        ),
        JourneyRunScript(
            runId = RUN_LANE_UNMAPPED_RUN_ID,
            sessionId = RUN_LANE_CREATED_SESSION_ID,
            createStatus = "started",
            observation =
                listOf(
                    JourneyRunEvent(type = RUN_LANE_UNMAPPED_EVENT_TYPE),
                    JourneyRunEvent(type = "run.completed"),
                ),
            finalStatus = "completed",
        ),
        JourneyRunScript(
            runId = RUN_LANE_REFUSED_RUN_ID,
            sessionId = RUN_LANE_CREATED_SESSION_ID,
            refuseCreate = true,
        ),
    )

internal fun laneRun(
    repositoryRoot: File,
    bearerToken: String,
    scenarioFactory: (descriptor: PinnedFixtureDescriptor, port: Int, bearerToken: String) -> JourneyScenario,
): LiveSessionMatrixRun {
    var startedProcess: JourneyGatewayProcess? = null
    val tlsClient = journeyTrustedClient(repositoryRoot.resolve(JOURNEY_CA_PEM_PATH))
    val fixture =
        DeterministicGatewayFixture(
            descriptorFile = repositoryRoot.resolve(PINNED_DESCRIPTOR_PATH),
            processFactory =
                GatewayProcessFactory { descriptor ->
                    val scenario = scenarioFactory(descriptor, freeLoopbackPort(), bearerToken)
                    val process =
                        JourneyGatewayProcess.start(
                            scenario,
                            repositoryRoot.resolve(JOURNEY_KEYSTORE_PATH),
                            JOURNEY_KEYSTORE_PASSWORD,
                        )
                    startedProcess = process
                    LoopbackHttpViewProcess(process)
                },
            readinessChecker = TlsLoopbackReadinessChecker(tlsClient, bearerToken),
        )
    return LiveSessionMatrixRun(
        fixture,
        tlsClient,
        LiveParityCaptureTransport(OkHttpGatewayTransport(tlsClient)),
    ) {
        requireNotNull(startedProcess) { "The lane journey process has not started." }
    }
}

internal fun laneClient(
    run: LiveSessionMatrixRun,
    bearerToken: String,
): DefaultGatewayClient =
    DefaultGatewayClient(
        endpoint = run.process.endpoint.toString(),
        bearerToken = bearerToken,
        transport = run.parityCaptures,
    )

internal fun gatewayFailure(block: () -> Unit): GatewayException =
    try {
        block()
        error("Expected the operation to fail with a GatewayException.")
    } catch (failure: GatewayException) {
        failure
    }

internal fun evidenceSink(name: String): File = File(evidenceDirectory(), "$name.jsonl")

internal fun LiveParityCapture.wireStatus(): Int =
    requireNotNull(status) { "The live parity capture for an executed operation records no wire status." }

internal fun laneRepositoryRoot(): File =
    File(
        requireNotNull(System.getProperty("fixture.repositoryRoot")) {
            "fixture.repositoryRoot must identify the repository root"
        },
    )

internal fun assertRecordedMethodsMatch(
    recorder: LiveSessionMatrixRecorder,
    operationMethods: Map<String, String>,
) {
    recorder.file.readLines().filter { it.isNotBlank() }.forEach { line ->
        val entry = Json.parseToJsonElement(line).jsonObject
        val operation = entry.getValue("operation").jsonPrimitive.content
        val recordedMethod = entry.getValue("method").jsonPrimitive.content
        val boundaryMethod =
            requireNotNull(operationMethods[operation]) {
                "Operation $operation is missing from the per-operation method table"
            }
        assertEquals("$operation must record the boundary method", boundaryMethod, recordedMethod)
    }
}

internal fun laneGatewayEndpoint(): URI? = System.getProperty(LIVE_GATEWAY_ENDPOINT_PROPERTY)?.let(URI::create)

internal fun laneGatewayBearerToken(): String? = System.getProperty(LIVE_GATEWAY_BEARER_TOKEN_PROPERTY)

internal class LiveGatewayEnvironmentRun(
    val endpoint: URI,
    val parityCaptures: LiveParityCaptureTransport,
)

internal fun environmentRun(endpoint: URI): LiveGatewayEnvironmentRun =
    LiveGatewayEnvironmentRun(
        endpoint = endpoint,
        parityCaptures = LiveParityCaptureTransport(OkHttpGatewayTransport()),
    )

internal fun laneClient(
    run: LiveGatewayEnvironmentRun,
    bearerToken: String,
): DefaultGatewayClient =
    DefaultGatewayClient(
        endpoint = run.endpoint.toString(),
        bearerToken = bearerToken,
        transport = run.parityCaptures,
    )

internal fun paritySink(name: String): File = File(parityArtifactDirectory(), "$name.jsonl")

internal class LiveSessionMatrixRun(
    val fixture: DeterministicGatewayFixture,
    val tlsClient: OkHttpClient,
    val parityCaptures: LiveParityCaptureTransport,
    private val startedProcess: () -> JourneyGatewayProcess,
) {
    val process: JourneyGatewayProcess
        get() = startedProcess()
}

internal class LoopbackHttpViewProcess(
    private val delegate: JourneyGatewayProcess,
) : GatewayProcess {
    val secureEndpoint: URI
        get() = delegate.endpoint

    override val endpoint: URI = URI("http", null, delegate.endpoint.host, delegate.endpoint.port, null, null, null)
    override val provenanceValue: String
        get() = delegate.provenanceValue
    override val isRunning: Boolean
        get() = delegate.isRunning

    override fun stop() {
        delegate.stop()
    }
}

internal class TlsLoopbackReadinessChecker(
    private val client: OkHttpClient,
    private val bearerToken: String,
) : FixtureReadinessChecker {
    override fun verify(
        process: GatewayProcess,
        descriptor: PinnedFixtureDescriptor,
    ) {
        val loopbackView = process as? LoopbackHttpViewProcess
        requireNotNull(loopbackView) {
            "The lane readiness check requires the loopback view of the started journey process."
        }
        require(process.isRunning) { "The journey Gateway process is not running." }
        val secureEndpoint = loopbackView.secureEndpoint
        val health = probe(secureEndpoint, descriptor.value("health_check.path"))
        val expectedHealthStatus = descriptor.value("health_check.expected_status")
        require(health.status == expectedHealthStatus.toInt()) {
            "Health check expected HTTP $expectedHealthStatus but received ${health.status}."
        }
        val capabilities = probe(secureEndpoint, descriptor.value("capability_check.path"))
        val expectedCapabilityStatus = descriptor.value("capability_check.expected_status")
        require(capabilities.status == expectedCapabilityStatus.toInt()) {
            "Capability check expected HTTP $expectedCapabilityStatus but received ${capabilities.status}."
        }
        requireAdvertisedCapabilities(capabilities.body)
    }

    private fun probe(
        endpoint: URI,
        path: String,
    ): ProbeResponse {
        val request =
            Request.Builder()
                .url(endpoint.resolve(path).toString())
                .header("Authorization", "Bearer $bearerToken")
                .build()
        client.newCall(request).execute().use { response ->
            return ProbeResponse(status = response.code, body = response.body.string())
        }
    }

    private fun requireAdvertisedCapabilities(body: String) {
        val root = Json.parseToJsonElement(body).jsonObject
        val advertised = root["endpoints"]?.jsonObject ?: error("Capability check response has no endpoints table.")
        PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.forEach { (endpointName, required) ->
            val advertisedEndpoint = advertised[endpointName]?.jsonObject
            val advertisedMethod = advertisedEndpoint?.get("method")?.jsonPrimitive?.content
            val advertisedPath = advertisedEndpoint?.get("path")?.jsonPrimitive?.content
            require(advertisedMethod?.equals(required.method, ignoreCase = true) == true) {
                "Capability check did not report the required capability $endpointName."
            }
            require(advertisedPath == required.path) {
                "Capability check reported the wrong path for the required capability $endpointName."
            }
        }
    }

    private data class ProbeResponse(
        val status: Int,
        val body: String,
    )
}

package org.mercurio.client.feature.entry.data

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.RunEventType
import org.mercurio.client.feature.entry.domain.RunId
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

private const val IDLE_GAP_RUN_ID = "idle-gap-run"

private const val KEEPALIVE_GAP_MILLIS = 10_200L

class RunEventStreamIdleGapTest {
    @Test
    fun observation_survives_idle_gaps_paced_like_the_deployed_gateway_keepalive() {
        val repositoryRoot = laneRepositoryRoot()
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                File(repositoryRoot, JOURNEY_KEYSTORE_PATH)
                    .inputStream()
                    .use { load(it, JOURNEY_KEYSTORE_PASSWORD.toCharArray()) }
            }
        val keyManagers =
            KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, JOURNEY_KEYSTORE_PASSWORD.toCharArray()) }
                .keyManagers
        val sslContext = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", freeLoopbackPort()), 0)
        server.httpsConfigurator = HttpsConfigurator(sslContext)
        server.createContext("/v1/runs/") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { body ->
                fun emit(chunk: String) {
                    body.write(chunk.toByteArray(StandardCharsets.UTF_8))
                    body.flush()
                }

                fun quietGap(comment: String) {
                    Thread.sleep(KEEPALIVE_GAP_MILLIS)
                    emit(comment)
                }
                emit(": open\n\n")
                emit(deltaFrame("Hello "))
                quietGap(": keepalive\n\n")
                emit(deltaFrame("after the gap."))
                quietGap(": keepalive\n\n")
                emit(
                    "data: {\"event\":\"run.completed\",\"run_id\":\"$IDLE_GAP_RUN_ID\",\"status\":\"succeeded\"}\n\n",
                )
                emit(": stream closed\n\n")
            }
        }
        server.start()
        try {
            val client =
                DefaultGatewayClient(
                    endpoint = "https://127.0.0.1:${server.address.port}",
                    bearerToken = "diag-token",
                    transport =
                        OkHttpGatewayTransport(
                            journeyTrustedClient(File(repositoryRoot, JOURNEY_CA_PEM_PATH)),
                        ),
                )
            val events = client.observeRun(RunId(IDLE_GAP_RUN_ID)).toList()
            assertTrue(
                "The observation must reach the terminal run.completed event across keepalive-paced idle gaps.",
                events.any { it.type == RunEventType.COMPLETED },
            )
        } finally {
            server.stop(0)
        }
    }

    private fun deltaFrame(text: String): String =
        "data: {\"event\":\"message.delta\",\"run_id\":\"$IDLE_GAP_RUN_ID\"," +
            "\"status\":\"running\",\"delta\":\"$text\"}\n\n"
}

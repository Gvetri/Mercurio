package org.mercurio.client.feature.entry.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

private val BODY = "mercurio-nightly-apk-bytes".toByteArray()

private const val ACCEPT = GitHubReleaseHttpRequest.GITHUB_ASSET_MEDIA_TYPE

private const val REDIRECT_LOCATION =
    "https://release-assets.githubusercontent.com/github-production-release-asset/1/mercurio.apk"

class OkHttpGitHubAssetHttpTransportTest {
    private lateinit var server: HttpServer

    private var baseUrl: String = ""

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() {
        server.stop(0)
    }

    @Test
    fun streams_a_successful_answer_into_the_sink_and_reports_its_size() {
        server.createContext("/asset") { exchange -> respond(exchange, status = 200, body = BODY) }
        val received = mutableListOf<Byte>()

        val stream = request("/asset") { buffer, length -> received.addAll(buffer.take(length)) }

        assertEquals(200, stream.statusCode)
        assertEquals(BODY.size.toLong(), stream.byteCount)
        assertEquals(BODY.toList(), received)
    }

    @Test
    fun reports_a_redirect_with_the_allowlisted_target_and_writes_no_body() {
        server.createContext("/asset") { exchange ->
            exchange.responseHeaders.add("Location", REDIRECT_LOCATION)
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        val received = mutableListOf<Byte>()

        val stream = request("/asset") { buffer, length -> received.addAll(buffer.take(length)) }

        assertEquals(302, stream.statusCode)
        assertEquals(REDIRECT_LOCATION, stream.location)
        assertEquals(0L, stream.byteCount)
        assertEquals(emptyList<Byte>(), received)
    }

    @Test
    fun reports_a_status_without_a_redirect_target() {
        server.createContext("/asset") { exchange -> respond(exchange, status = 503, body = ByteArray(0)) }

        val stream = request("/asset") { _, _ -> }

        assertEquals(503, stream.statusCode)
        assertNull(stream.location)
    }

    @Test
    fun raises_the_transport_failure_when_the_host_does_not_answer() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val transport = OkHttpGitHubAssetHttpTransport()

        assertThrows(IOException::class.java) {
            runBlocking {
                transport.stream(GitHubReleaseHttpRequest("http://127.0.0.1:$closedPort/asset", ACCEPT)) { _, _ -> }
            }
        }
    }

    private fun request(
        path: String,
        sink: GitHubAssetSink,
    ): GitHubAssetStream =
        runBlocking {
            OkHttpGitHubAssetHttpTransport()
                .stream(GitHubReleaseHttpRequest("$baseUrl$path", ACCEPT), sink)
        }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: ByteArray,
    ) {
        if (body.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        exchange.close()
    }
}

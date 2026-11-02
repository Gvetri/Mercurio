package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class MessageImageHttpTransportTest {
    @Test
    fun a_public_connection_returns_the_response_fields() =
        runBlocking {
            OneShotHttpServer().use { server ->
                val transport = OkHttpMessageImageHttpTransport(addressPolicy = { true })

                val response = transport.execute(MessageImageHttpRequest(server.url()))

                assertEquals(200, response.statusCode)
                assertEquals("image/png", response.contentType)
                assertArrayEquals(byteArrayOf(1, 2, 3), response.body)
            }
        }

    @Test
    fun a_non_public_connection_is_refused() =
        runBlocking {
            OneShotHttpServer().use { server ->
                val transport = OkHttpMessageImageHttpTransport()

                assertEquals(
                    MessageImageTransportException.Kind.UNSAFE_ADDRESS,
                    failureKind(transport, server.url()),
                )
                server.close()

                assertFalse(server.sawRequest.get())
            }
        }

    @Test
    fun an_oversized_declared_body_is_refused() =
        runBlocking {
            OneShotHttpServer(headers = listOf("Content-Type: image/png", "Content-Length: 10")).use { server ->
                val transport = OkHttpMessageImageHttpTransport(addressPolicy = { true }, maxBodyBytes = 4)

                assertEquals(
                    MessageImageTransportException.Kind.TOO_LARGE,
                    failureKind(transport, server.url()),
                )
            }
        }

    @Test
    fun a_body_over_the_cap_is_refused_when_no_length_is_declared() =
        runBlocking {
            OneShotHttpServer(headers = listOf("Content-Type: image/png"), body = ByteArray(10)).use { server ->
                val transport = OkHttpMessageImageHttpTransport(addressPolicy = { true }, maxBodyBytes = 4)

                assertEquals(
                    MessageImageTransportException.Kind.TOO_LARGE,
                    failureKind(transport, server.url()),
                )
            }
        }

    @Test
    fun a_connection_failure_is_a_network_failure() =
        runBlocking {
            val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val closedPort = socket.localPort
            socket.close()
            val transport = OkHttpMessageImageHttpTransport(addressPolicy = { true })

            assertEquals(
                MessageImageTransportException.Kind.NETWORK,
                failureKind(transport, "http://127.0.0.1:$closedPort/chart.png"),
            )
        }
}

private suspend fun failureKind(
    transport: MessageImageHttpTransport,
    url: String,
): MessageImageTransportException.Kind? {
    val failure = runCatching { transport.execute(MessageImageHttpRequest(url)) }.exceptionOrNull()
    return (failure as? MessageImageTransportException)?.kind
}

private class OneShotHttpServer(
    private val headers: List<String> = listOf("Content-Type: image/png", "Content-Length: 3"),
    private val body: ByteArray = byteArrayOf(1, 2, 3),
) : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    val sawRequest = AtomicBoolean(false)

    private val port: Int = server.localPort

    private val worker =
        Thread {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val firstLine = reader.readLine()
                    if (firstLine != null) {
                        sawRequest.set(true)
                        readRequestHead(reader)
                    }
                    val head = (listOf("HTTP/1.1 200 OK") + headers).joinToString(separator = "\r\n")
                    socket.getOutputStream().apply {
                        write((head + "\r\n\r\n").toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }.apply {
            name = "message-image-http-fixture"
            isDaemon = true
            start()
        }

    fun url(): String = "http://127.0.0.1:$port/chart.png"

    override fun close() {
        server.close()
        worker.join(JOIN_TIMEOUT_MILLIS)
    }

    private companion object {
        const val JOIN_TIMEOUT_MILLIS = 5_000L
    }
}

private fun readRequestHead(reader: BufferedReader) {
    var line = reader.readLine()
    while (!line.isNullOrEmpty()) {
        line = reader.readLine() ?: return
    }
}

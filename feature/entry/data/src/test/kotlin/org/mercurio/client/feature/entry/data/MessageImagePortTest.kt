package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.MessageImageFetch
import org.mercurio.client.feature.entry.domain.MessageImageRefusal

@OptIn(ExperimentalCoroutinesApi::class)
class MessageImagePortTest {
    @Test
    fun https_image_is_fetched_with_its_media_type() =
        runTest {
            val transport = FakeTransport { imageResponse() }
            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertFetched(result, byteArrayOf(1, 2, 3), "image/png")
            assertEquals(listOf("https://cdn.example.com/chart.png"), transport.requestedUrls)
        }

    @Test
    fun non_https_and_relative_destinations_are_refused_without_a_request() =
        runTest {
            val transport = FakeTransport { imageResponse() }

            assertEquals(
                MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_DESTINATION),
                port(transport).fetch("http://cdn.example.com/chart.png"),
            )
            assertEquals(
                MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_DESTINATION),
                port(transport).fetch("chart.png"),
            )
            assertTrue(transport.requestedUrls.isEmpty())
        }

    @Test
    fun a_private_literal_address_is_refused_without_a_request() =
        runTest {
            val transport = FakeTransport { imageResponse() }
            val resolver = MapHostResolver(mapOf("192.168.1.10" to listOf("192.168.1.10")))

            val result = port(transport, resolver).fetch("https://192.168.1.10/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_ADDRESS), result)
            assertTrue(transport.requestedUrls.isEmpty())
        }

    @Test
    fun a_hostname_that_resolves_to_loopback_is_refused() =
        runTest {
            val transport = FakeTransport { imageResponse() }
            val resolver = MapHostResolver(mapOf("localhost" to listOf("127.0.0.1")))

            val result = port(transport, resolver).fetch("https://localhost/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_ADDRESS), result)
            assertTrue(transport.requestedUrls.isEmpty())
        }

    @Test
    fun a_hostname_with_one_private_answer_is_refused() =
        runTest {
            val transport = FakeTransport { imageResponse() }
            val resolver =
                MapHostResolver(mapOf("mixed.example" to listOf("93.184.216.34", "fd00::1")))

            val result = port(transport, resolver).fetch("https://mixed.example/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_ADDRESS), result)
            assertTrue(transport.requestedUrls.isEmpty())
        }

    @Test
    fun an_unresolvable_host_is_a_network_refusal() =
        runTest {
            val transport = FakeTransport { imageResponse() }
            val resolver = MessageImageHostResolver { throw java.net.UnknownHostException("missing") }

            val result = port(transport, resolver).fetch("https://missing.example/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.NETWORK), result)
        }

    @Test
    fun an_https_redirect_is_followed() =
        runTest {
            val transport =
                FakeTransport { url ->
                    if (url == "https://cdn.example.com/chart.png") {
                        MessageImageHttpResponse(302, null, "https://cdn2.example.com/chart.png", ByteArray(0))
                    } else {
                        imageResponse()
                    }
                }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertFetched(result, byteArrayOf(1, 2, 3), "image/png")
            assertEquals(2, transport.requestedUrls.size)
        }

    @Test
    fun a_redirect_to_http_is_refused() =
        runTest {
            val transport =
                FakeTransport {
                    MessageImageHttpResponse(301, null, "http://cdn.example.com/chart.png", ByteArray(0))
                }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_DESTINATION), result)
        }

    @Test
    fun a_redirect_without_a_location_is_a_network_refusal() =
        runTest {
            val transport = FakeTransport { MessageImageHttpResponse(302, null, null, ByteArray(0)) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.NETWORK), result)
        }

    @Test
    fun a_redirect_cycle_is_bounded() =
        runTest {
            val transport =
                FakeTransport { url ->
                    MessageImageHttpResponse(302, null, "https://cdn.example.com/chart.png", ByteArray(0))
                }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.NETWORK), result)
            assertEquals(4, transport.requestedUrls.size)
        }

    @Test
    fun an_empty_body_is_refused() =
        runTest {
            val transport = FakeTransport { MessageImageHttpResponse(200, "image/png", null, ByteArray(0)) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.EMPTY_RESPONSE), result)
        }

    @Test
    fun a_non_image_media_type_is_refused_before_decode() =
        runTest {
            val transport =
                FakeTransport {
                    MessageImageHttpResponse(200, "text/html; charset=utf-8", null, byteArrayOf(1, 2, 3))
                }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSUPPORTED_CONTENT_TYPE), result)
        }

    @Test
    fun media_type_parameters_are_accepted() =
        runTest {
            val transport =
                FakeTransport {
                    MessageImageHttpResponse(200, "image/webp; charset=binary", null, byteArrayOf(9))
                }

            val result = port(transport).fetch("https://cdn.example.com/chart.webp")

            assertFetched(result, byteArrayOf(9), "image/webp; charset=binary")
        }

    @Test
    fun a_media_type_that_is_only_the_prefix_is_refused() =
        runTest {
            val transport = FakeTransport { MessageImageHttpResponse(200, "image/", null, byteArrayOf(1)) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSUPPORTED_CONTENT_TYPE), result)
        }

    @Test
    fun a_non_success_status_is_a_network_refusal() =
        runTest {
            val transport = FakeTransport { MessageImageHttpResponse(404, "image/png", null, byteArrayOf(1)) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.NETWORK), result)
        }

    @Test
    fun a_transport_timeout_is_a_timeout_refusal() =
        runTest {
            val transport = FakeTransport { throw transportFailure(MessageImageTransportException.Kind.TIMEOUT) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.TIMEOUT), result)
        }

    @Test
    fun an_oversized_body_is_refused() =
        runTest {
            val transport = FakeTransport { throw transportFailure(MessageImageTransportException.Kind.TOO_LARGE) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.TOO_LARGE), result)
        }

    @Test
    fun a_transport_failure_is_a_network_refusal() =
        runTest {
            val transport = FakeTransport { throw transportFailure(MessageImageTransportException.Kind.NETWORK) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.NETWORK), result)
        }

    @Test
    fun a_connection_to_a_non_public_address_is_an_unsafe_address_refusal() =
        runTest {
            val transport = FakeTransport { throw transportFailure(MessageImageTransportException.Kind.UNSAFE_ADDRESS) }

            val result = port(transport).fetch("https://cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_ADDRESS), result)
        }

    @Test
    fun a_user_info_destination_is_refused() =
        runTest {
            val transport = FakeTransport { imageResponse() }

            val result = port(transport).fetch("https://user:secret@cdn.example.com/chart.png")

            assertEquals(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_DESTINATION), result)
        }

    @Test
    fun cancellation_stops_the_request() =
        runTest {
            var cancelled = false
            val transport =
                object : MessageImageHttpTransport {
                    override suspend fun execute(request: MessageImageHttpRequest): MessageImageHttpResponse {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                }
            val job = launch { port(transport).fetch("https://cdn.example.com/chart.png") }
            runCurrent()

            job.cancel()
            job.join()

            assertTrue(cancelled)
        }

    private fun assertFetched(
        result: MessageImageFetch,
        bytes: ByteArray,
        contentType: String,
    ) {
        val fetched = result as? MessageImageFetch.Fetched
        if (fetched == null) throw AssertionError("Expected a fetched image, but the result was $result")
        assertArrayEquals(bytes, fetched.bytes)
        assertEquals(contentType, fetched.contentType)
    }

    private fun TestScope.port(
        transport: MessageImageHttpTransport,
        resolver: MessageImageHostResolver = MapHostResolver(),
    ): HttpMessageImagePort = HttpMessageImagePort(transport, resolver, StandardTestDispatcher(testScheduler))

    private fun imageResponse(): MessageImageHttpResponse {
        return MessageImageHttpResponse(200, "image/png", null, byteArrayOf(1, 2, 3))
    }

    private fun transportFailure(kind: MessageImageTransportException.Kind): MessageImageTransportException =
        MessageImageTransportException(kind, "The transport refused the request.")
}

private class FakeTransport(
    private val answer: suspend (String) -> MessageImageHttpResponse,
) : MessageImageHttpTransport {
    val requestedUrls = mutableListOf<String>()

    override suspend fun execute(request: MessageImageHttpRequest): MessageImageHttpResponse {
        requestedUrls += request.url
        return answer(request.url)
    }
}

private class MapHostResolver(
    private val byHost: Map<String, List<String>> = emptyMap(),
    private val fallback: List<String> = listOf("93.184.216.34"),
) : MessageImageHostResolver {
    override suspend fun resolve(host: String): List<String> = byHost[host] ?: fallback
}

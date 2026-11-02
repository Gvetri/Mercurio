package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.MessageImageFetch
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.domain.MessageImageRefusal
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MessageImageStateTest {
    @Test
    fun a_fetched_and_decoded_image_is_loaded() =
        runTest {
            val image = ImageBitmap(2, 3)
            val services = services(port = FixedStatePort(fetched()), decoder = FixedStateDecoder(image))

            val state = services.load("https://cdn.example.com/chart.png")

            assertEquals(MessageImageState.Loaded(image), state)
        }

    @Test
    fun an_undecodable_image_is_a_decode_failure() =
        runTest {
            val services = services(port = FixedStatePort(fetched()), decoder = FixedStateDecoder(null))

            val state = services.load("https://cdn.example.com/chart.png")

            assertEquals(MessageImageState.Failed(MessageImageFailure.DECODE), state)
        }

    @Test
    fun an_unsafe_address_is_an_unsafe_failure_and_is_never_decoded() =
        runTest {
            val decoder = CountingStateDecoder(ImageBitmap(1, 1))
            val services =
                services(
                    port = FixedStatePort(MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_ADDRESS)),
                    decoder = decoder,
                )

            val state = services.load("https://192.168.1.10/chart.png")

            assertEquals(MessageImageState.Failed(MessageImageFailure.LOAD), state)
            assertEquals(0, decoder.calls)
        }

    @Test
    fun a_network_refusal_is_a_network_failure() =
        runTest {
            val services =
                services(
                    port = FixedStatePort(MessageImageFetch.Refused(MessageImageRefusal.NETWORK)),
                    decoder = FixedStateDecoder(ImageBitmap(1, 1)),
                )

            val state = services.load("https://cdn.example.com/chart.png")

            assertEquals(MessageImageState.Failed(MessageImageFailure.LOAD), state)
        }

    @Test
    fun a_timeout_is_a_timeout_failure() =
        runTest {
            val services =
                services(
                    port = SuspendingStatePort(),
                    decoder = FixedStateDecoder(ImageBitmap(1, 1)),
                    timeoutMillis = 1_000L,
                )

            val state = services.load("https://cdn.example.com/chart.png")

            assertEquals(MessageImageState.Failed(MessageImageFailure.LOAD), state)
        }

    @Test
    fun cancellation_stops_the_fetch_instead_of_settling_a_state() =
        runTest {
            val port = SuspendingStatePort()
            val services = services(port = port, decoder = FixedStateDecoder(ImageBitmap(1, 1)))
            val job = launch { services.load("https://cdn.example.com/chart.png") }
            runCurrent()

            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertTrue(port.cancelled)
        }

    @Test
    fun loads_are_bounded_to_the_declared_concurrency() =
        runTest {
            val port = ConcurrencyTrackingPort()
            val services =
                services(
                    port = port,
                    decoder = FixedStateDecoder(ImageBitmap(1, 1)),
                    concurrency = Semaphore(2),
                )

            List(5) { index -> async { services.load("https://cdn.example.com/$index.png") } }.awaitAll()

            assertEquals(2, port.peakConcurrency)
        }

    @Test
    fun every_refusal_maps_to_its_terminal_failure() =
        runTest {
            val refusals =
                listOf(
                    MessageImageRefusal.UNSAFE_DESTINATION,
                    MessageImageRefusal.UNSAFE_ADDRESS,
                    MessageImageRefusal.EMPTY_RESPONSE,
                    MessageImageRefusal.UNSUPPORTED_CONTENT_TYPE,
                    MessageImageRefusal.TOO_LARGE,
                    MessageImageRefusal.NETWORK,
                    MessageImageRefusal.TIMEOUT,
                )

            refusals.forEach { refusal ->
                val services =
                    services(
                        port = FixedStatePort(MessageImageFetch.Refused(refusal)),
                        decoder = FixedStateDecoder(ImageBitmap(1, 1)),
                    )

                val state = services.load("https://cdn.example.com/chart.png")

                assertEquals(refusal.name, MessageImageState.Failed(MessageImageFailure.LOAD), state)
            }
        }

    @Test
    fun copy_distinguishes_a_decode_failure_from_a_load_failure() {
        assertEquals(R.string.chat_image_decode_failed, MessageImageFailure.DECODE.messageRes)
        assertEquals(R.string.chat_image_load_failed, MessageImageFailure.LOAD.messageRes)
        assertFalse(MessageImageFailure.LOAD.messageRes == MessageImageFailure.DECODE.messageRes)
    }

    private fun services(
        port: MessageImagePort,
        decoder: MessageImageDecoder,
        concurrency: Semaphore = Semaphore(MAX_CONCURRENT_IMAGE_LOADS),
        timeoutMillis: Long = IMAGE_LOAD_TIMEOUT_MILLIS,
    ): MessageImageServices =
        MessageImageServices(
            loadImage = port::fetch,
            decoder = decoder,
            autoLoad = true,
            concurrency = concurrency,
            timeoutMillis = timeoutMillis,
        )

    private fun fetched(): MessageImageFetch.Fetched = MessageImageFetch.Fetched(byteArrayOf(1, 2, 3), "image/png")
}

private class FixedStatePort(
    private val result: MessageImageFetch,
) : MessageImagePort {
    override suspend fun fetch(destination: String): MessageImageFetch = result
}

private class SuspendingStatePort : MessageImagePort {
    var cancelled = false

    override suspend fun fetch(destination: String): MessageImageFetch {
        try {
            awaitCancellation()
        } finally {
            cancelled = true
        }
    }
}

private class ConcurrencyTrackingPort : MessageImagePort {
    private var active = 0
    var peakConcurrency = 0

    override suspend fun fetch(destination: String): MessageImageFetch {
        active++
        peakConcurrency = maxOf(peakConcurrency, active)
        delay(10)
        active--
        return MessageImageFetch.Fetched(byteArrayOf(1), "image/png")
    }
}

private class FixedStateDecoder(
    private val image: ImageBitmap?,
) : MessageImageDecoder {
    override suspend fun decode(bytes: ByteArray): ImageBitmap? = image
}

private class CountingStateDecoder(
    private val image: ImageBitmap?,
) : MessageImageDecoder {
    var calls = 0

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        calls++
        return image
    }
}

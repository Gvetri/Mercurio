package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.MessageImageFetch
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.domain.MessageImageRefusal
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
class MessageImageCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_shows_the_alt_text_and_the_progress_copy() {
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(
                    LocalMessageImageServices provides services(SuspendingCardPort(), FixedCardDecoder()),
                ) {
                    MessageImageCard(destination = "https://cdn.example.com/chart.png", alt = ALT)
                }
            }
        }

        composeTestRule.onNodeWithText(ALT).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading image…").assertIsDisplayed()
    }

    @Test
    fun a_failed_image_offers_retry_and_a_retry_loads_the_image() {
        val port = RetryPort()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalMessageImageServices provides services(port, FixedCardDecoder())) {
                    MessageImageCard(destination = "https://cdn.example.com/chart.png", alt = ALT)
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { port.calls >= 1 }
        composeTestRule.onNodeWithText("Image could not be loaded.").assertIsDisplayed()

        composeTestRule.onNodeWithText("[ try again ]").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(ALT).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(ALT).assertIsDisplayed()
    }

    @Test
    fun a_decode_failure_has_its_own_copy() {
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(
                    LocalMessageImageServices provides
                        services(FixedCardPort(fetched()), FixedCardDecoder(image = null)),
                ) {
                    MessageImageCard(destination = "https://cdn.example.com/chart.png", alt = ALT)
                }
            }
        }

        composeTestRule.onNodeWithText("Image could not be decoded.").assertIsDisplayed()
    }

    @Test
    fun the_decoder_keeps_no_disk_cache() {
        val decoder = CoilMessageImageDecoder(RuntimeEnvironment.getApplication())

        assertNull(decoder.imageLoader.diskCache)
    }

    @Test
    fun leaving_the_viewport_cancels_the_load() {
        val port = CancellationTrackingPort()
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(LocalMessageImageServices provides services(port, FixedCardDecoder())) {
                    if (visible) {
                        MessageImageCard(destination = "https://cdn.example.com/chart.png", alt = ALT)
                    }
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { port.calls == 1 }

        visible = false
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.mainClock.advanceTimeBy(100L)

        assertTrue(port.cancelled)
    }

    @Test
    fun an_image_inside_a_list_item_loads() {
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(
                    LocalMessageImageServices provides services(FixedCardPort(fetched()), FixedCardDecoder()),
                ) {
                    MarkdownContent(
                        markdown = "- ![$ALT](https://cdn.example.com/chart.png)",
                        actions = RecordingActions().asMessageActions(),
                        onTableExpand = {},
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(ALT).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(ALT).assertIsDisplayed()
    }

    @Test
    fun an_image_without_a_provider_stays_literal_text() {
        composeTestRule.setContent {
            HermesTheme {
                MarkdownContent(
                    markdown = "![chart](https://cdn.example.com/chart.png)",
                    actions = RecordingActions().asMessageActions(),
                    onTableExpand = {},
                )
            }
        }

        composeTestRule.onNodeWithText("![chart](https://cdn.example.com/chart.png)").assertIsDisplayed()
    }

    @Test
    fun a_relative_image_stays_literal_text_even_with_a_provider() {
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(
                    LocalMessageImageServices provides services(FixedCardPort(fetched()), FixedCardDecoder()),
                ) {
                    MarkdownContent(
                        markdown = "![chart](charts/chart.png)",
                        actions = RecordingActions().asMessageActions(),
                        onTableExpand = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("![chart](charts/chart.png)").assertIsDisplayed()
    }

    @Test
    fun auto_load_off_waits_for_a_tap_and_then_loads_the_image() {
        val port = CountingPort()
        composeTestRule.setContent {
            HermesTheme {
                CompositionLocalProvider(
                    LocalMessageImageServices provides services(port, FixedCardDecoder(), autoLoad = false),
                ) {
                    MessageImageCard(destination = "https://cdn.example.com/chart.png", alt = ALT)
                }
            }
        }

        composeTestRule.onNodeWithText("Image not loaded.").assertIsDisplayed()
        assertEquals(0, port.calls)

        composeTestRule.onNodeWithText("Load image").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(ALT).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(ALT).assertIsDisplayed()
        assertEquals(1, port.calls)
    }

    private fun services(
        port: MessageImagePort,
        decoder: MessageImageDecoder,
        autoLoad: Boolean = true,
    ): MessageImageServices = MessageImageServices(port::fetch, decoder, autoLoad)

    private fun fetched(): MessageImageFetch.Fetched = MessageImageFetch.Fetched(byteArrayOf(1, 2, 3), "image/png")

    private companion object {
        const val ALT = "Latency by minute for run 4b2a."
    }
}

private class SuspendingCardPort : MessageImagePort {
    override suspend fun fetch(destination: String): MessageImageFetch = awaitCancellation()
}

private class FixedCardPort(
    private val result: MessageImageFetch,
) : MessageImagePort {
    override suspend fun fetch(destination: String): MessageImageFetch = result
}

private class CountingPort : MessageImagePort {
    @Volatile var calls = 0

    override suspend fun fetch(destination: String): MessageImageFetch {
        calls++
        return MessageImageFetch.Fetched(byteArrayOf(1, 2, 3), "image/png")
    }
}

private class CancellationTrackingPort : MessageImagePort {
    @Volatile var calls = 0

    @Volatile var cancelled = false

    override suspend fun fetch(destination: String): MessageImageFetch {
        calls++
        try {
            awaitCancellation()
        } finally {
            cancelled = true
        }
    }
}

private class RetryPort : MessageImagePort {
    var calls = 0

    override suspend fun fetch(destination: String): MessageImageFetch {
        calls++
        return if (calls == 1) {
            MessageImageFetch.Refused(MessageImageRefusal.NETWORK)
        } else {
            MessageImageFetch.Fetched(byteArrayOf(1, 2, 3), "image/png")
        }
    }
}

private class FixedCardDecoder(
    private val image: ImageBitmap? = ImageBitmap(4, 3),
) : MessageImageDecoder {
    override suspend fun decode(bytes: ByteArray): ImageBitmap? = image
}

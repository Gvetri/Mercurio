package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.mercurio.client.feature.entry.domain.MessageImageFetch

/**
 * Loads Message images with bounded concurrency and one timeout, and turns every fetch or decode
 * outcome into a terminal state. A caller cancels the surrounding coroutine when the image leaves
 * the viewport, which cancels the request instead of letting it finish unseen.
 */
internal class MessageImageServices(
    private val loadImage: suspend (String) -> MessageImageFetch,
    private val decoder: MessageImageDecoder,
    val autoLoad: Boolean,
    private val concurrency: Semaphore = Semaphore(MAX_CONCURRENT_IMAGE_LOADS),
    private val timeoutMillis: Long = IMAGE_LOAD_TIMEOUT_MILLIS,
) {
    suspend fun load(destination: String): MessageImageState =
        concurrency.withPermit {
            withTimeoutOrNull(timeoutMillis) {
                when (val fetch = loadImage(destination)) {
                    is MessageImageFetch.Fetched -> fetch.bytes.toImageState()
                    is MessageImageFetch.Refused -> MessageImageState.Failed(MessageImageFailure.LOAD)
                }
            } ?: MessageImageState.Failed(MessageImageFailure.LOAD)
        }

    private suspend fun ByteArray.toImageState(): MessageImageState =
        decoder.decode(this)?.let(MessageImageState::Loaded) ?: MessageImageState.Failed(MessageImageFailure.DECODE)
}

internal const val MAX_CONCURRENT_IMAGE_LOADS = 3

internal const val IMAGE_LOAD_TIMEOUT_MILLIS = 15_000L

package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.mercurio.client.feature.entry.domain.MessageImageFetch

/**
 * The truthful terminal state of one Message image. Nothing but a loaded bitmap can reach
 * [Loaded], so a failed or still-loading image never leaves a stale picture on screen.
 */
internal sealed interface MessageImageState {
    data object Loading : MessageImageState

    data class Loaded(
        val image: ImageBitmap,
    ) : MessageImageState

    data class Failed(
        val failure: MessageImageFailure,
    ) : MessageImageState
}

/** Why a Message image did not load; the copy is chosen in one place. */
internal enum class MessageImageFailure {
    LOAD,
    DECODE,
}

internal val MessageImageFailure.messageRes: Int
    get() =
        when (this) {
            MessageImageFailure.LOAD -> R.string.chat_image_load_failed
            MessageImageFailure.DECODE -> R.string.chat_image_decode_failed
        }

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

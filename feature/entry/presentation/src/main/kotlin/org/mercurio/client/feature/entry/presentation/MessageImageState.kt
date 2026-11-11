package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.ImageBitmap

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

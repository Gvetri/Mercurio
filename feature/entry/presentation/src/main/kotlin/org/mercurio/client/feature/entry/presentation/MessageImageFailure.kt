package org.mercurio.client.feature.entry.presentation

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

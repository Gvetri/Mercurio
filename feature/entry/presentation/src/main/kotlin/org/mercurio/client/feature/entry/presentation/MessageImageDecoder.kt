package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes fetched bytes into a drawable image; a test double replaces it. */
internal fun interface MessageImageDecoder {
    suspend fun decode(bytes: ByteArray): ImageBitmap?
}

package org.mercurio.client.feature.entry.presentation

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

/** Decodes fetched bytes into a drawable image; a test double replaces it. */
internal fun interface MessageImageDecoder {
    suspend fun decode(bytes: ByteArray): ImageBitmap?
}

/**
 * The Coil decoder. The loader holds no disk cache and no network component: bytes already passed
 * the domain port's guardrails, so this class only turns them into a bounded bitmap and reports an
 * undecodable body as null instead of an exception.
 */
internal class CoilMessageImageDecoder(
    context: Context,
) : MessageImageDecoder {
    private val applicationContext = context.applicationContext

    internal val imageLoader =
        ImageLoader
            .Builder(applicationContext)
            .diskCache(null)
            .build()

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        val request =
            ImageRequest
                .Builder(applicationContext)
                .data(bytes)
                .size(MAX_DECODE_SIZE_PX)
                .build()
        return when (val result = imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap(result.image.width, result.image.height).asImageBitmap()
            else -> null
        }
    }
}

private const val MAX_DECODE_SIZE_PX = 1_080

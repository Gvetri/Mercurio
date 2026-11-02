package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mercurio.client.feature.entry.domain.MessageImageFetch

/**
 * Provides the Message image loader to the renderer. [loadImage] is the domain port's fetch
 * function, so the renderer holds no port itself; the decoder is the Coil bitmap decoder and the
 * renderer reaches neither an HTTP stack nor the platform's own network API.
 */
@Composable
fun MessageImageProvider(
    loadImage: suspend (String) -> MessageImageFetch,
    autoLoad: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val services =
        remember(loadImage, context, autoLoad) {
            MessageImageServices(loadImage, CoilMessageImageDecoder(context), autoLoad)
        }
    CompositionLocalProvider(LocalMessageImageServices provides services, content = content)
}

internal val LocalMessageImageServices = staticCompositionLocalOf<MessageImageServices?> { null }

@Composable
internal fun MessageImageCard(
    destination: String,
    alt: String,
) {
    val services = LocalMessageImageServices.current ?: return
    var attempt by remember(destination) { mutableIntStateOf(0) }
    var requested by remember(destination) { mutableStateOf(false) }
    if (!services.autoLoad && !requested) {
        MessageImageStage(
            alt = alt,
            note = stringResource(R.string.chat_image_not_loaded),
            isError = false,
            actionLabel = stringResource(R.string.chat_image_load),
            onAction = { requested = true },
        )
        return
    }
    val state by
        produceState<MessageImageState>(MessageImageState.Loading, destination, attempt, services) {
            value = MessageImageState.Loading
            value = services.load(destination)
        }
    when (val current = state) {
        MessageImageState.Loading ->
            MessageImageStage(alt = alt, note = stringResource(R.string.chat_image_loading), isError = false)
        is MessageImageState.Failed ->
            MessageImageStage(
                alt = alt,
                note = stringResource(current.failure.messageRes),
                isError = true,
                actionLabel = stringResource(R.string.chat_try_again_label),
                onAction = { attempt++ },
            )
        is MessageImageState.Loaded -> LoadedMessageImage(image = current.image, alt = alt)
    }
}

@Composable
private fun MessageImageFrame(content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier.fillMaxWidth().messageImageStyle(shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Modifier.messageImageStyle(shape: Shape): Modifier {
    val tokens = LocalHermesDesignTokens.current
    return this
        .clip(shape)
        .background(tokens.componentColors.inset)
        .border(IMAGE_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant, shape)
}

@Composable
private fun MessageImageStage(
    alt: String,
    note: String,
    isError: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalHermesDesignTokens.current.spacing
    MessageImageFrame {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = IMAGE_STAGE_HEIGHT)
                    .padding(spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MessageImageAlt(alt = alt)
            Text(
                text = note,
                style =
                    if (isError) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                textAlign = TextAlign.Center,
            )
            if (onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(text = actionLabel ?: stringResource(R.string.chat_try_again_label))
                }
            }
        }
    }
}

@Composable
private fun MessageImageAlt(alt: String) {
    if (alt.isBlank()) return
    Text(
        text = alt,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = IMAGE_ALT_MAX_WIDTH),
    )
}

@Composable
private fun LoadedMessageImage(
    image: ImageBitmap,
    alt: String,
) {
    val shape = MaterialTheme.shapes.medium
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val aspectRatio = if (image.height == 0) 1f else image.width.toFloat() / image.height
        val imageHeight = (maxWidth / aspectRatio).coerceIn(MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT)
        Image(
            bitmap = image,
            contentDescription = alt.ifBlank { stringResource(R.string.chat_image_content_description) },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(imageHeight).messageImageStyle(shape),
        )
    }
}

private val IMAGE_BORDER_WIDTH = 1.dp

private val IMAGE_STAGE_HEIGHT = 150.dp

private val IMAGE_ALT_MAX_WIDTH = 250.dp

private val MIN_IMAGE_HEIGHT = 96.dp

private val MAX_IMAGE_HEIGHT = 240.dp

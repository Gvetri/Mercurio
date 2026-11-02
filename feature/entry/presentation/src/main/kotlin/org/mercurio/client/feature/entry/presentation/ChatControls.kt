package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Pack D text control: a label that carries an in-place action, with the 48 dp target the
 * design reserves for it. The accent marks it while it can run; a disabled control keeps the
 * 38 % content alpha instead of a second colour.
 */
@Composable
internal fun ChatTextControl(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val tokens = LocalHermesDesignTokens.current
    val color =
        if (enabled) {
            tokens.colorScheme.primary
        } else {
            tokens.colorScheme.onSurface.copy(alpha = CHAT_DISABLED_ALPHA)
        }
    Box(
        modifier =
            modifier
                .widthIn(min = CHAT_TOUCH_TARGET)
                .heightIn(min = CHAT_TOUCH_TARGET)
                .padding(horizontal = 2.dp)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                .clickable(role = Role.Button, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** A glyph the design draws but does not enable yet: it stays visible, inert, and 48 dp wide. */
@Composable
internal fun ChatDisabledGlyph(
    painter: Painter,
    description: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Box(
        modifier =
            modifier
                .widthIn(min = CHAT_TOUCH_TARGET)
                .heightIn(min = CHAT_TOUCH_TARGET)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(CHAT_GLYPH_SIZE),
            tint = tokens.colorScheme.onSurfaceVariant.copy(alpha = CHAT_SOON_ALPHA),
        )
    }
}

/** The caret that marks the tail of a row the Run is still writing. */
@Composable
internal fun ChatStreamingCaret(modifier: Modifier = Modifier) {
    val tokens = LocalHermesDesignTokens.current
    Box(
        modifier =
            modifier
                .padding(start = 2.dp)
                .width(1.dp)
                .height(CHAT_CARET_HEIGHT)
                .background(tokens.colorScheme.primary),
    )
}

/** The 12 dp progress ring the pending row carries in place of its time. */
@Composable
internal fun ChatSpinner(modifier: Modifier = Modifier) {
    val tokens = LocalHermesDesignTokens.current
    val inProgressDescription = stringResource(R.string.chat_in_progress)
    CircularProgressIndicator(
        modifier =
            modifier.size(CHAT_SPINNER_SIZE).semantics {
                contentDescription = inProgressDescription
            },
        color = tokens.colorScheme.primary,
        trackColor = tokens.colorScheme.primary.copy(alpha = CHAT_SPINNER_TRACK_ALPHA),
        strokeWidth = 2.dp,
    )
}

/** The dotted three-dot trigger at the size the design draws it, with a 48 dp target. */
@Composable
internal fun ChatDotsControl(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Box(
        modifier =
            modifier
                .widthIn(min = CHAT_TOUCH_TARGET)
                .heightIn(min = CHAT_TOUCH_TARGET)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.width(CHAT_DOTS_WIDTH).height(CHAT_DOTS_HEIGHT), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_dots),
                contentDescription = null,
                modifier = Modifier.size(CHAT_DOTS_GLYPH_SIZE),
                tint = tokens.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The chevron the top bar draws for the back gesture's visible counterpart. */
@Composable
internal fun ChatBackControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val backDescription = stringResource(R.string.action_back_to_sessions)
    Box(
        modifier =
            modifier
                .widthIn(min = CHAT_TOUCH_TARGET)
                .heightIn(min = CHAT_TOUCH_TARGET)
                .semantics { contentDescription = backDescription }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.width(CHAT_NAV_WIDTH).height(CHAT_NAV_HEIGHT), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(CHAT_NAV_GLYPH_SIZE),
                tint = tokens.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val CHAT_TOUCH_TARGET = 48.dp

internal val CHAT_GLYPH_SIZE = 17.dp

internal val CHAT_CARET_HEIGHT = 15.dp

internal val CHAT_SPINNER_SIZE = 12.dp

internal const val CHAT_DISABLED_ALPHA = 0.38f

private val CHAT_DOTS_WIDTH = 32.dp

private val CHAT_DOTS_HEIGHT = 24.dp

private val CHAT_DOTS_GLYPH_SIZE = 14.dp

private val CHAT_NAV_WIDTH = 32.dp

private val CHAT_NAV_HEIGHT = 32.dp

private val CHAT_NAV_GLYPH_SIZE = 16.dp

private const val CHAT_SOON_ALPHA = 0.45f

private const val CHAT_SPINNER_TRACK_ALPHA = 0.24f

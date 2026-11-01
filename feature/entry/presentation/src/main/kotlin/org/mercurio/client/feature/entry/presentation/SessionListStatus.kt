package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** The 24 dp status strip: the list count and state on the left, the Gateway state on the right. */
@Composable
internal fun SessionListStatusStrip(state: SessionListUiState) {
    val tokens = LocalHermesDesignTokens.current
    val status = state.statusUiState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = STATUS_STRIP_HEIGHT)
                .clip(tokens.shapes.small)
                .background(tokens.componentColors.inset)
                .padding(horizontal = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        status.segments.forEach { segment ->
            Text(
                text = segment,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = status.gateway,
            style = MaterialTheme.typography.labelMedium,
            color =
                when (status.gatewayStatus) {
                    GatewayStatus.READY -> tokens.colorScheme.primary
                    GatewayStatus.WAITING -> tokens.colorScheme.onSurfaceVariant
                    GatewayStatus.OFFLINE -> tokens.colorScheme.error
                },
            maxLines = 1,
        )
    }
}

/** The empty, no-match, and empty-unavailable states share one centered composition. */
@Composable
internal fun SessionListEmptyContent(
    state: SessionListUiState,
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val content = emptyContentFor(state)
    Column(
        modifier = modifier.fillMaxSize().padding(tokens.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge,
            color = tokens.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content.body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        if (content.actionLabel != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.s))
            Box(
                modifier =
                    Modifier
                        .heightIn(min = tokens.spacing.xxxl)
                        .clickable(role = Role.Button) { onEvent(EntryUiEvent.RefreshSessionListClicked) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = content.actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colorScheme.primary,
                )
            }
        }
        SessionCreationErrorBanner(state = state)
    }
}

private data class SessionListEmptyCopy(
    val title: String,
    val body: String,
    val actionLabel: String? = null,
)

private fun emptyContentFor(state: SessionListUiState): SessionListEmptyCopy =
    when {
        state.searchQuery.isNotBlank() ->
            SessionListEmptyCopy(
                title = "No sessions match",
                body = "Search covers the titles and previews already loaded.",
            )
        state.isUnavailable || state.isStale ->
            SessionListEmptyCopy(
                title = "Sessions are unavailable",
                body = "The Gateway is offline. Cached sessions are not available.",
                actionLabel = "Try again",
            )
        else ->
            SessionListEmptyCopy(
                title = "No sessions",
                body = "The Gateway returned no sessions. Create one to start.",
            )
    }

@Composable
internal fun SessionCreationErrorBanner(state: SessionListUiState) {
    val tokens = LocalHermesDesignTokens.current
    val message = state.creationErrorCategory?.safeMessage ?: return
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = tokens.colorScheme.error,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)
                .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
internal fun NewSessionRow(
    enabled: Boolean,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NEW_SESSION_ROW_HEIGHT)
                    .clickable(enabled = enabled, role = Role.Button) { onEvent(EntryUiEvent.CreateSessionClicked) }
                    .padding(horizontal = tokens.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA),
            )
            Text(
                text = "new session",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA),
            )
        }
    }
}

@Composable
internal fun SessionListFooter(label: String) {
    val tokens = LocalHermesDesignTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = tokens.spacing.xxxl)
                    .padding(horizontal = tokens.spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = tokens.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(tokens.spacing.s))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SessionSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.16.em),
        color = tokens.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.m,
                    end = tokens.spacing.m,
                    top = 10.dp,
                    bottom = tokens.spacing.xs,
                ),
    )
}

internal fun LazyListScope.sessionListSkeletonItems() {
    items(SKELETON_ROW_COUNT) {
        SessionSkeletonRow()
    }
}

@Composable
private fun SessionSkeletonRow() {
    val tokens = LocalHermesDesignTokens.current
    val skeletonColor = tokens.colorScheme.onSurface.copy(alpha = SKELETON_ALPHA)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = tokens.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(24.dp)
                        .height(10.dp)
                        .clip(tokens.shapes.small)
                        .background(skeletonColor),
            )
            Spacer(modifier = Modifier.width(tokens.spacing.l))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(SKELETON_TITLE_WIDTH)
                            .height(12.dp)
                            .clip(tokens.shapes.small)
                            .background(skeletonColor),
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(SKELETON_PREVIEW_WIDTH)
                            .height(9.dp)
                            .clip(tokens.shapes.small)
                            .background(skeletonColor),
                )
            }
        }
    }
}

private val STATUS_STRIP_HEIGHT = 24.dp
private val NEW_SESSION_ROW_HEIGHT = 38.dp
private const val SKELETON_ROW_COUNT = 6
private const val SKELETON_TITLE_WIDTH = 0.62f
private const val SKELETON_PREVIEW_WIDTH = 0.84f
private const val SKELETON_ALPHA = 0.08f
private const val DISABLED_CONTENT_ALPHA = 0.38f

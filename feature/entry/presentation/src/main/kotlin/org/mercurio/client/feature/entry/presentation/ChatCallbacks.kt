package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.graphics.Color
import org.mercurio.client.feature.entry.domain.RunId

/** The two label colours one row variant needs: its role label and its time cell. */
internal data class ChatHeaderColors(
    val label: Color,
    val time: Color,
)

/**
 * The callbacks one transcript row needs: the platform Message actions, whether a retry can run
 * now, the retry itself, and the screen's event channel. Passing one value keeps every row
 * signature small and keeps a new callback from reaching only half the rows.
 */
internal data class ChatCallbacks(
    val actions: MessageActions,
    val retryEnabled: Boolean,
    val onRetry: (RunId) -> Unit,
    val onEvent: (EntryUiEvent) -> Unit,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId

/**
 * The callbacks one transcript row needs: the platform Message actions, whether a retry can run
 * now, the retry itself, and the screen's event channel. Passing one value keeps every row
 * signature small and keeps a new callback from reaching only half the rows.
 */
internal data class ChatCallbacks(
    val actions: MessageActions,
    val retryEnabled: Boolean,
    val onRetry: (RunId) -> Unit,
    val onTableExpand: (MarkdownBlock.Table) -> Unit,
    val onEvent: (EntryUiEvent) -> Unit,
)

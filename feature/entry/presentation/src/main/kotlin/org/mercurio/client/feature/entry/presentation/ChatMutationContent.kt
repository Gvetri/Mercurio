package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The transient Session mutation surface above the run line: the inline rename editor, or the
 * pending and failed feedback a Session action produces. The approved Chat design does not draw
 * a steady state for it; it appears only while a Session action is in flight.
 */
@Composable
internal fun ChatMutationContent(
    detail: SessionDetailState,
    mutation: SessionMutationUiState?,
    onEvent: (EntryUiEvent) -> Unit,
) {
    val tokens = LocalHermesDesignTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.s)) {
        SessionActionControls(
            session = detail.content.session,
            mutation = mutation,
            enabled = detail.actionsEnabled && mutation?.pendingAction == null,
            onEvent = onEvent,
        )
    }
}

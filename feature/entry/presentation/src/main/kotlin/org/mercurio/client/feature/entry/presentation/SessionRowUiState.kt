package org.mercurio.client.feature.entry.presentation

internal data class SessionRowUiState(
    val session: SessionItemUiState,
    val mutation: SessionMutationUiState?,
    val selected: Boolean,
    val enabled: Boolean,
    val mutationEnabled: Boolean,
    val onClick: () -> Unit,
)

package org.mercurio.client.feature.entry.presentation

data class SessionMutationUiState(
    val rename: SessionRenameUiState? = null,
    val delete: SessionDeleteUiState? = null,
    val pendingAction: SessionMutationAction? = null,
    val errorCategory: SessionMutationErrorCategory? = null,
    val retryAction: SessionMutationAction? = null,
)

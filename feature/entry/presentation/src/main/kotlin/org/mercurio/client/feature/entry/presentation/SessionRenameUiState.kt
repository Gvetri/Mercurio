package org.mercurio.client.feature.entry.presentation

data class SessionRenameUiState(
    val titleDraft: String,
    val isSubmitting: Boolean = false,
    val errorCategory: SessionRenameErrorCategory? = null,
)

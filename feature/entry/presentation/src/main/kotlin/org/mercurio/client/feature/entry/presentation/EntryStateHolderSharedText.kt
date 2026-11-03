package org.mercurio.client.feature.entry.presentation

internal const val SHARED_TEXT_MIME_TYPE = "text/plain"

internal fun EntryStateHolder.dispatchIntakeEvent(event: EntryUiEvent.Intake) {
    when (event) {
        is EntryUiEvent.InboundUri -> handleInboundUri(event.rawUri)
        is EntryUiEvent.SharedText -> handleSharedText(event)
        is EntryUiEvent.SharedTextDismissed -> discardSharedText()
    }
}

/**
 * Handles a shared payload from another app. Only plain text is accepted, and it
 * is held in memory only until the user picks a Session; nothing is persisted
 * and nothing sends before that pick. A share with no connection, or with an
 * unsupported payload, is discarded with a truthful message.
 */
internal fun EntryStateHolder.handleSharedText(event: EntryUiEvent.SharedText) {
    val sharedText = event.sharedTextOrNull()
    if (sharedText == null) {
        rejectSharedText(InboundLinkNoticeCategory.SHARE_UNSUPPORTED)
        return
    }
    val state = mutableUiState.value
    if (!state.isConnected || state.sessionList == null) {
        rejectSharedText(InboundLinkNoticeCategory.SHARE_NEEDS_CONNECTION)
        return
    }
    synchronized(sessionRequestLock) {
        pendingSharedText = sharedText
        mutableUiState.value =
            mutableUiState.value.copy(
                isSharedTextSessionPickerActive = true,
                inboundLinkNotice = InboundLinkNoticeCategory.SHARE_PICK_SESSION,
            )
    }
    dismissInboundLinkSurfaces()
    returnToSessionList()
    synchronized(sessionRequestLock) {
        mutableUiState.value =
            mutableUiState.value.copy(
                isSharedTextSessionPickerActive = true,
                inboundLinkNotice = InboundLinkNoticeCategory.SHARE_PICK_SESSION,
            )
    }
}

internal fun EntryStateHolder.discardSharedText() {
    synchronized(sessionRequestLock) {
        pendingSharedText = null
        val current = mutableUiState.value
        mutableUiState.value =
            current.copy(
                isSharedTextSessionPickerActive = false,
                inboundLinkNotice =
                    current.inboundLinkNotice
                        ?.takeUnless { it == InboundLinkNoticeCategory.SHARE_PICK_SESSION },
            )
    }
}

private fun EntryStateHolder.rejectSharedText(notice: InboundLinkNoticeCategory) {
    synchronized(sessionRequestLock) {
        pendingSharedText = null
        mutableUiState.value =
            mutableUiState.value.copy(
                isSharedTextSessionPickerActive = false,
                inboundLinkNotice = notice,
            )
    }
}

private fun EntryUiEvent.SharedText.sharedTextOrNull(): String? =
    text?.takeIf {
        !isMultiple &&
            !hasStream &&
            mimeType.equals(SHARED_TEXT_MIME_TYPE, ignoreCase = true) &&
            it.isNotBlank()
    }

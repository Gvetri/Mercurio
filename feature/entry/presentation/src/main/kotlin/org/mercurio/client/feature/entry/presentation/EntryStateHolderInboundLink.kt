package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.showInboundLinkNotice(notice: InboundLinkNoticeCategory) {
    synchronized(sessionRequestLock) {
        mutableUiState.value = mutableUiState.value.copy(inboundLinkNotice = notice)
    }
}

/**
 * Dismisses the app-level surfaces a link navigates away from: Settings, Local
 * diagnostics, the connection dialogs, and an idle credential-change form. It
 * changes no stored connection and keeps the typed credential value in state.
 */
internal fun EntryStateHolder.dismissInboundLinkSurfaces() {
    cancelCredentialRotation()
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value
        mutableUiState.value =
            current.copy(
                isSettingsOpen = false,
                isRemoveConfirmationOpen = false,
                isClearHistoryCacheConfirmationOpen = false,
                localDiagnostics = current.localDiagnostics.copy(isOpen = false),
            )
    }
}

internal fun EntryStateHolder.handleInboundUri(rawUri: String?) {
    parseEntryLink(rawUri)?.let(::applyInboundLink)
}

internal fun EntryStateHolder.openLinkedComposer() {
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value
        val sessionList = current.sessionList ?: return
        val opened = sessionList.openedSession
        if (opened == null) {
            mutableUiState.value =
                current.copy(inboundLinkNotice = InboundLinkNoticeCategory.COMPOSE_LINK_NEEDS_SESSION)
            return
        }
        nextComposerFocusRequestId += 1
        mutableUiState.value =
            current.copy(
                inboundLinkNotice = null,
                sessionList =
                    sessionList.copy(
                        openedSession =
                            opened.copy(
                                composerFocusRequestId = nextComposerFocusRequestId,
                            ),
                    ),
            )
    }
}

internal fun EntryStateHolder.openLinkedSession(sessionId: String?) {
    val usableId = sessionId?.takeIf(String::isNotBlank)
    if (usableId == null) {
        showUnknownInboundLink()
        return
    }
    if (startSessionOpen(SessionId(usableId), linked = true)) {
        dismissInboundLinkSurfaces()
    }
}

internal fun EntryStateHolder.showUnknownInboundLink() {
    dismissInboundLinkSurfaces()
    returnToSessionList()
    val interruptedOpen = inboundLinkListRequestInterrupted
    inboundLinkListRequestInterrupted = false
    if (clearCancelledListRequest() || interruptedOpen) {
        refreshSessionList()
    }
    showInboundLinkNotice(InboundLinkNoticeCategory.SESSION_LINK_UNKNOWN)
}

internal fun EntryStateHolder.clearCancelledListRequest(): Boolean =
    synchronized(sessionRequestLock) {
        val current = mutableUiState.value
        current.sessionList
            ?.takeIf(SessionListUiState::interruptsListRequest)
            ?.let { sessionList ->
                mutableUiState.value =
                    current.copy(
                        sessionList =
                            sessionList.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isSearching = false,
                                isLoadingMore = false,
                            ),
                    )
                true
            } ?: false
    }

internal fun EntryStateHolder.applyInboundLink(link: EntryLink) {
    val state = mutableUiState.value
    if (!state.isConnected || state.sessionList == null) {
        showInboundLinkNotice(link.connectionNotice())
        return
    }
    when (link) {
        is EntryLink.Session -> openLinkedSession(link.id)
        EntryLink.Compose -> {
            dismissInboundLinkSurfaces()
            openLinkedComposer()
        }
    }
}

internal fun EntryStateHolder.showLinkedSessionOpenFailure(requestGeneration: Long) {
    val isCurrent =
        synchronized(sessionRequestLock) {
            requestGeneration == sessionRequestGeneration
        }
    if (!isCurrent) return
    showUnknownInboundLink()
}

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionId

data class SessionListUiState(
    val sessions: List<SessionItemUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val isUnavailable: Boolean = false,
    val errorCategory: SessionListErrorCategory? = null,
    val creationErrorCategory: SessionCreationErrorCategory? = null,
    val showFirstUseGuidance: Boolean = false,
    val openingSessionId: SessionId? = null,
    val openedSession: OpenSessionUiState? = null,
    val createSession: SessionCreationUiState? = null,
    val searchQuery: String = "",
    val nextOffset: Int? = null,
    val isLoadingMore: Boolean = false,
    val isSearching: Boolean = false,
    val sessionMutations: Map<SessionId, SessionMutationUiState> = emptyMap(),
    val modelPicker: ModelPickerUiState = ModelPickerUiState(),
) {
    val hasPendingMutation: Boolean
        get() = sessionMutations.values.any { it.pendingAction != null }

    /**
     * Search covers only server-provided Session titles and previews that this client
     * already loaded; the pinned `GET /api/sessions` has no general search parameter
     * (its `title` filter is an exact-title lookup) and transcripts are never fetched.
     */
    val visibleSessions: List<SessionItemUiState>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return sessions
            return sessions.filter { session ->
                session.title.contains(query, ignoreCase = true) ||
                    session.preview?.contains(query, ignoreCase = true) == true
            }
        }
}

internal val SessionListUiState.hasActiveRequest: Boolean
    get() =
        isLoading ||
            isRefreshing ||
            isLoadingMore ||
            isSearching ||
            openingSessionId != null ||
            createSession != null ||
            hasPendingMutation ||
            openedSession?.isSending == true ||
            openedSession?.isRefreshing == true

internal fun SessionListUiState.allowsSessionMutation(): Boolean =
    !hasActiveRequest &&
        !isUnavailable &&
        openedSession?.isRefreshing != true &&
        openedSession?.isReconciliationInProgress != true

internal fun SessionListUiState.createSessionActionEnabled(): Boolean =
    !isLoading &&
        !isUnavailable &&
        openingSessionId == null &&
        createSession == null &&
        !hasPendingMutation

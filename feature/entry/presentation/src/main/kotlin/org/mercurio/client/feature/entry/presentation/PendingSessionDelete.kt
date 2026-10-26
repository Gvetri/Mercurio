package org.mercurio.client.feature.entry.presentation

/**
 * The pending delete confirmation resolved at the entry root: which session is awaiting the
 * app-modal delete confirmation, its mutation state, and whether the list currently allows a
 * mutation. Resolution prefers the opened session, then the first list session with a pending
 * delete, so the confirmation also stays reachable for sessions outside the visible page.
 */
internal data class PendingSessionDelete(
    val session: SessionItemUiState,
    val mutation: SessionMutationUiState,
    val allowsMutation: Boolean,
)

internal fun SessionListUiState.pendingSessionDelete(): PendingSessionDelete? {
    val openedId = openedSession?.session?.id
    val pendingDeletes: Sequence<Pair<SessionItemUiState, SessionMutationUiState>> =
        (sessions.asSequence() + listOfNotNull(openedSession?.session))
            .distinctBy { it.id }
            .mapNotNull { candidate ->
                sessionMutations[candidate.id]
                    ?.takeIf { it.delete != null }
                    ?.let { candidate to it }
            }
    val entry = pendingDeletes.firstOrNull { it.first.id == openedId } ?: pendingDeletes.firstOrNull()
    val (session, mutation) = entry ?: return null
    return PendingSessionDelete(
        session = session,
        mutation = mutation,
        allowsMutation = allowsSessionMutation(),
    )
}

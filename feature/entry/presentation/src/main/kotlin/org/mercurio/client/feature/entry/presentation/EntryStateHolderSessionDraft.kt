package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionDraftKey
import org.mercurio.client.feature.entry.domain.SessionId

internal fun EntryStateHolder.sessionDraftKey(
    sessionId: SessionId,
    endpoint: String = mutableUiState.value.endpoint,
): SessionDraftKey = SessionDraftKey(endpoint, sessionId)

/**
 * Restores the composer draft for a Session: the in-memory draft when one exists,
 * otherwise the persisted draft from the last session on this device (an app restart
 * keeps the in-memory maps empty). A persisted non-empty draft is backfilled into the
 * in-memory map so the recovery paths read the same text. Callers hold
 * [EntryStateHolder.sessionRequestLock] (or re-enter it).
 */
internal fun EntryStateHolder.restoredSessionDraft(sessionId: SessionId): String =
    synchronized(sessionRequestLock) {
        sessionDrafts[sessionId]
            ?: sessionDraftStore.read(sessionDraftKey(sessionId))?.takeIf { it.isNotEmpty() }?.also { persisted ->
                sessionDrafts[sessionId] = persisted
                if (sessionId !in sessionDraftRevisions) {
                    sessionDraftRevisions[sessionId] = 0L
                }
            }
    }.orEmpty()

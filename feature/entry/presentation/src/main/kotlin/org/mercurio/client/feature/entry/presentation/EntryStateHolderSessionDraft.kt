package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.SessionDraftKey
import org.mercurio.client.feature.entry.domain.SessionId

internal const val SHARED_TEXT_SEPARATOR = "\n\n"

internal fun EntryStateHolder.sessionDraftKey(
    sessionId: SessionId,
    endpoint: String = mutableUiState.value.endpoint,
): SessionDraftKey = SessionDraftKey(endpoint, sessionId)

/**
 * Restores the composer draft for a Session: the in-memory draft when one exists,
 * otherwise the persisted draft from the last session on this device (an app restart
 * keeps the in-memory maps empty). A persisted non-empty draft is backfilled into the
 * in-memory map so the recovery paths read the same text. Callers run on the state-change
 * lane.
 */
internal fun EntryStateHolder.restoredSessionDraft(sessionId: SessionId): String =
    run {
        sessionDrafts[sessionId]
            ?: sessionDraftStore.read(sessionDraftKey(sessionId))?.takeIf { it.isNotEmpty() }?.also { persisted ->
                sessionDrafts[sessionId] = persisted
                sessionDraftRevisions.putIfAbsent(sessionId, 0L)
            }
    }.orEmpty()

/**
 * The composer draft for a successfully opened Session. A pending shared text
 * becomes part of the draft of the first Session that opens: it is written
 * through the existing draft store and consumed exactly once, so it never sends
 * and never lands in any other storage. A Session that already holds an unsent
 * draft keeps it; the shared text is appended below it, so picking a Session
 * never destroys typed text. Without a pending share this is the restored
 * draft. Callers run on the state-change lane.
 */
internal fun EntryStateHolder.sessionDraftForOpenedSession(sessionId: SessionId): String =
    run {
        val shared = pendingSharedText
        if (shared == null) {
            restoredSessionDraft(sessionId)
        } else {
            pendingSharedText = null
            val existing = restoredSessionDraft(sessionId)
            val merged = if (existing.isEmpty()) shared else existing + SHARED_TEXT_SEPARATOR + shared
            sessionDrafts[sessionId] = merged
            sessionDraftRevisions.merge(sessionId, 1L, Long::plus)
            sessionDraftStore.write(sessionDraftKey(sessionId), merged)
            merged
        }
    }

internal fun EntryStateHolder.updateComposerText(value: String) {
    val current = mutableUiState.value.sessionList ?: return
    val opened = current.openedSession ?: return
    sessionDrafts[opened.session.id] = value
    sessionDraftRevisions.merge(opened.session.id, 1L, Long::plus)
    sessionDraftStore.write(sessionDraftKey(opened.session.id), value)
    sessionSendErrors.remove(opened.session.id)
    mutableUiState.value =
        mutableUiState.value.copy(
            sessionList =
                current.copy(
                    openedSession =
                        opened.copy(
                            composerText = value,
                            sendErrorCategory = sendErrorCategoryFor(opened.session.id),
                        ),
                ),
        )
}

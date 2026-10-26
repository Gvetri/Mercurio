package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.application.OpenedSession
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunObservationState
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isActive

internal fun OpenSessionUiState.withSessionMetadata(sessions: List<Session>): OpenSessionUiState =
    sessions.firstOrNull { it.id == session.id }
        ?.let { copy(session = it.toSessionItemUiState()) }
        ?: this

internal fun EntryStateHolder.mergeSessions(
    existing: List<SessionItemUiState>,
    incoming: List<SessionItemUiState>,
): List<SessionItemUiState> = (existing + incoming).associateBy { it.id }.values.toList().orderedSessions()

internal fun EntryStateHolder.historyRunIdToReconcile(
    sessionId: SessionId,
    openedSession: OpenedSession,
): RunId? =
    synchronized(sessionRequestLock) {
        val hasActiveLocalRun = sessionRuns[sessionId].orEmpty().any(Run::isActive)
        val runIdToReconcile =
            openedSession.history.latestRun()?.id ?: sessionRuns[sessionId].orEmpty().latestRun()?.id
        runIdToReconcile?.takeUnless {
            hasUnresolvedSubmission(sessionId) && !hasActiveLocalRun
        }
    }

internal fun EntryStateHolder.observedMessageUiState(observation: RunObservationState?): SessionMessageUiState? =
    observation?.let { current ->
        current.toSessionMessageUiState(
            retryInput = submittedRunInputs[current.run.id],
            modelSelection = submittedRunModelSelections[current.run.id],
        )
    }

internal fun EntryStateHolder.toMessageUiStates(history: SessionHistory): List<SessionMessageUiState> {
    return history.messages.map { message ->
        val original =
            message.runId?.let { id ->
                submittedRunInputs[id]
                    ?: history.messages
                        .lastOrNull { it.runId == id && it.role == "user" }
                        ?.content
                        ?.takeIf { it.isNotBlank() }
            }
        message.toSessionMessageUiState(
            retryInput = original,
            modelSelection = message.runId?.let(submittedRunModelSelections::get),
        )
    }.chronological()
}

internal fun List<SessionItemUiState>.orderedSessions(): List<SessionItemUiState> {
    return filter { it.pinned } + filterNot { it.pinned }
}

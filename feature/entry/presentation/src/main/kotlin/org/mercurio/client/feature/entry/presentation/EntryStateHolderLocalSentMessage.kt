package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId

internal fun EntryStateHolder.localUserMessage(run: Run): SessionMessageUiState? {
    val input = submittedRunInputs[run.id]?.takeIf(String::isNotBlank) ?: return null
    return SessionMessageUiState(
        id = localUserMessageId(run.id),
        role = "user",
        content = input,
        runId = run.id,
    )
}

internal fun localUserMessageId(runId: RunId): String = "local-user:${runId.value}"

internal fun List<SessionMessageUiState>.withLocalUser(message: SessionMessageUiState?): List<SessionMessageUiState> =
    when {
        message == null -> this
        any { it.id == message.id || (it.runId == message.runId && it.role == message.role) } -> this
        else -> this + message
    }

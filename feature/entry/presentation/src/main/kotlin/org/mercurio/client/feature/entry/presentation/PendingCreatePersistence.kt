package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey

internal data class PendingCreatePersistence(
    val key: RecoverySessionKey,
    val pendingCreate: PendingCreateState,
    val started: Boolean,
)

internal fun EntryStateHolder.pendingCreateSnapshot(): List<PendingCreatePersistence> =
    pendingCreateSessions.map { (key, pendingCreate) ->
        PendingCreatePersistence(key = key, pendingCreate = pendingCreate, started = key in pendingCreateStarted)
    }

internal fun EntryStateHolder.persistPendingCreatesOffThread(pendingCreates: List<PendingCreatePersistence>) {
    if (pendingCreates.isEmpty()) return
    persistenceScope.launch { writePendingCreateMarkers(pendingCreates) }
}

internal fun EntryStateHolder.writePendingCreateMarkers(pendingCreates: List<PendingCreatePersistence>) {
    pendingCreates.forEach { pending ->
        val submissionKey = PendingRunSubmissionKey(pending.key.endpoint, pending.key.sessionId)
        val persisted =
            runCatching {
                runSubmissionUncertaintyStore.add(
                    submissionKey,
                    pending.pendingCreate.knownRunIds,
                    pending.pendingCreate.attemptId,
                )
            }.getOrDefault(false)
        if (persisted && !pending.started) {
            runCatching {
                runSubmissionUncertaintyStore.markSettled(submissionKey, pending.pendingCreate.attemptId)
            }
        }
    }
}

package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey

internal data class PendingCreatePersistence(
    val key: RecoverySessionKey,
    val pendingCreate: PendingCreateState,
    val started: Boolean,
)

/**
 * Captures the in-memory pending-create state so its markers can be written after the maps are
 * cleared. Callers hold [EntryStateHolder.sessionRequestLock], so a concurrent send cannot slip
 * a new entry between the snapshot and the clear.
 */
internal fun EntryStateHolder.pendingCreateSnapshot(): List<PendingCreatePersistence> =
    pendingCreateSessions.map { (key, pendingCreate) ->
        PendingCreatePersistence(key = key, pendingCreate = pendingCreate, started = key in pendingCreateStarted)
    }

/**
 * Writes the captured pending-create markers on [EntryStateHolder.persistenceScope], so a close
 * never writes storage on the caller's thread. An empty snapshot starts nothing.
 */
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

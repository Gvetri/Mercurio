package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot

internal object ProcessRunSubmissionUncertaintyStore : UncertaintyStoreState() {
    override suspend fun bindRun(
        key: PendingRunSubmissionKey,
        runId: RunId,
        attemptId: String?,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key] ?: return false
            if (attemptId != null && current.attemptId != attemptId) return false
            current.boundRunId = runId
            true
        }

    override fun boundRunId(key: PendingRunSubmissionKey): RunId? = synchronized(lock) { keys[key]?.boundRunId }

    override suspend fun markSettled(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key] ?: return false
            if (attemptId != null && current.attemptId != attemptId) return false
            current.settled = true
            true
        }

    override fun isSettled(key: PendingRunSubmissionKey): Boolean = synchronized(lock) { keys[key]?.settled == true }

    override suspend fun markAmbiguous(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key] ?: return false
            if (attemptId != null && current.attemptId != attemptId) return false
            current.requiresRunMatch = true
            true
        }

    override fun requiresRunMatch(key: PendingRunSubmissionKey): Boolean =
        synchronized(lock) {
            keys[key]?.requiresRunMatch == true
        }

    override suspend fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key]
            if (
                current == null ||
                current.knownRunIds.toSet() != knownRunIds ||
                !current.matchesAttempt(attemptId)
            ) {
                false
            } else {
                keys.remove(key)
                true
            }
        }

    private fun UncertaintyRecord.matchesSnapshot(snapshot: RunSubmissionUncertaintySnapshot): Boolean =
        attemptId == snapshot.attemptId &&
            knownRunIds.toSet() == snapshot.knownRunIds &&
            boundRunId == snapshot.boundRunId &&
            settled == snapshot.settled &&
            requiresRunMatch == snapshot.requiresRunMatch

    override suspend fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key] ?: return@synchronized false
            if (current.matchesSnapshot(snapshot)) {
                keys.remove(key)
                true
            } else {
                false
            }
        }
}

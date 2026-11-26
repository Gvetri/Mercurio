package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot

internal object ProcessRunSubmissionUncertaintyStore : UncertaintyStoreState() {
    override suspend fun bindRun(
        key: PendingRunSubmissionKey,
        runId: RunId,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { record -> record.copy(boundRunId = runId) }

    override fun boundRunId(key: PendingRunSubmissionKey): RunId? = records.get()[key]?.boundRunId

    override suspend fun markSettled(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { record -> record.copy(settled = true) }

    override fun isSettled(key: PendingRunSubmissionKey): Boolean = records.get()[key]?.settled == true

    override suspend fun markAmbiguous(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { record -> record.copy(requiresRunMatch = true) }

    override fun requiresRunMatch(key: PendingRunSubmissionKey): Boolean = records.get()[key]?.requiresRunMatch == true

    override suspend fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean =
        removeIfMatching(key) { record ->
            record.knownRunIds == knownRunIds && record.matchesAttempt(attemptId)
        }

    override suspend fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean = removeIfMatching(key) { record -> record.matchesSnapshot(snapshot) }

    private fun UncertaintyRecord.matchesSnapshot(snapshot: RunSubmissionUncertaintySnapshot): Boolean =
        attemptId == snapshot.attemptId &&
            knownRunIds == snapshot.knownRunIds &&
            boundRunId == snapshot.boundRunId &&
            settled == snapshot.settled &&
            requiresRunMatch == snapshot.requiresRunMatch
}

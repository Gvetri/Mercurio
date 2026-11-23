package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyReader
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore

private const val LEGACY_ATTEMPT_ID = "legacy"

private val submissionUncertaintyMutex = Mutex()

/**
 * Persists response-loss markers over one storage record per pending submission.
 *
 * Every mutation, including [remove], serializes through one process-wide [Mutex] across a suspend
 * boundary, so two store instances over the same storage cannot interleave a check-and-write.
 * Reads take no lock: the storage commits one immutable record per key atomically, so a reader
 * observes either the previous snapshot or the next complete one.
 */
class DefaultRunSubmissionUncertaintyStore(
    private val storage: RunSubmissionUncertaintyStorage,
) : RunSubmissionUncertaintyStore,
    RunSubmissionUncertaintyReader by RunSubmissionUncertaintyReads(storage) {
    override suspend fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean =
        submissionUncertaintyMutex.withLock {
            val current = storage.read(key)
            if (current != null && attemptId != null && current.attemptId != attemptId) return@withLock false
            val base =
                current
                    ?: RunSubmissionUncertaintySnapshot(attemptId ?: LEGACY_ATTEMPT_ID, emptySet(), null, false, false)
            val next =
                base.copy(
                    knownRunIds = current?.knownRunIds.orEmpty() + knownRunIds,
                )
            storage.write(key, next)
            true
        }

    override suspend fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean =
        submissionUncertaintyMutex.withLock {
            val current = storage.read(key)
            val removable = current != null && (attemptId == null || current.attemptId == attemptId)
            if (removable) storage.remove(key)
            removable
        }

    override fun contains(key: PendingRunSubmissionKey): Boolean = storage.read(key) != null

    override suspend fun clearEndpoint(endpoint: String) {
        submissionUncertaintyMutex.withLock {
            storage.clearEndpoint(endpoint)
        }
    }

    override suspend fun bindRun(
        key: PendingRunSubmissionKey,
        runId: RunId,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { it.copy(boundRunId = runId) }

    override suspend fun markSettled(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { it.copy(settled = true) }

    override suspend fun markAmbiguous(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean = updateIfMatching(key, attemptId) { it.copy(requiresRunMatch = true) }

    override suspend fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean =
        submissionUncertaintyMutex.withLock {
            val current = storage.read(key) ?: return@withLock false
            if (
                (attemptId != null && current.attemptId != attemptId) ||
                current.knownRunIds != knownRunIds
            ) {
                return@withLock false
            }
            storage.remove(key)
            true
        }

    override suspend fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean =
        submissionUncertaintyMutex.withLock {
            if (storage.read(key) != snapshot) return@withLock false
            storage.remove(key)
            true
        }

    private suspend fun updateIfMatching(
        key: PendingRunSubmissionKey,
        attemptId: String?,
        transform: (RunSubmissionUncertaintySnapshot) -> RunSubmissionUncertaintySnapshot,
    ): Boolean =
        submissionUncertaintyMutex.withLock {
            val current = storage.read(key) ?: return@withLock false
            if (attemptId != null && current.attemptId != attemptId) return@withLock false
            storage.write(key, transform(current))
            true
        }
}

private class RunSubmissionUncertaintyReads(
    private val storage: RunSubmissionUncertaintyStorage,
) : RunSubmissionUncertaintyReader {
    override fun knownRunIds(key: PendingRunSubmissionKey): Set<RunId> = storage.read(key)?.knownRunIds.orEmpty()

    override fun attemptId(key: PendingRunSubmissionKey): String? = storage.read(key)?.attemptId

    override fun snapshot(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? = storage.read(key)

    override fun boundRunId(key: PendingRunSubmissionKey): RunId? = storage.read(key)?.boundRunId

    override fun isSettled(key: PendingRunSubmissionKey): Boolean = storage.read(key)?.settled == true

    override fun requiresRunMatch(key: PendingRunSubmissionKey): Boolean = storage.read(key)?.requiresRunMatch == true
}

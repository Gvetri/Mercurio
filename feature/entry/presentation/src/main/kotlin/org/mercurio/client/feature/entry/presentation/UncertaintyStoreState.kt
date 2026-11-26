package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import java.util.concurrent.atomic.AtomicReference

internal const val LEGACY_ATTEMPT_ID = "legacy"

/**
 * In-memory uncertainty records over one immutable snapshot map.
 *
 * Every mutation serializes through one [Mutex] across the suspend boundary the port already
 * offers, and reads take no lock: a mutation publishes the next complete map through [records],
 * so a reader observes either the previous record or the next complete one. No mutation can run
 * inside the critical section: [Mutex.withLock] takes a non-suspending action, and every mutating
 * method here is `suspend`.
 */
internal sealed class UncertaintyStoreState : RunSubmissionUncertaintyStore {
    protected val writeMutex = Mutex()
    protected val records = AtomicReference<Map<PendingRunSubmissionKey, UncertaintyRecord>>(emptyMap())

    override suspend fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean =
        writeMutex.withLock {
            val current = records.get()[key]
            if (current != null && attemptId != null && current.attemptId != attemptId) {
                false
            } else {
                val record = current ?: UncertaintyRecord(attemptId = attemptId ?: LEGACY_ATTEMPT_ID)
                val next = record.copy(knownRunIds = record.knownRunIds + knownRunIds)
                records.set(records.get() + (key to next))
                true
            }
        }

    override suspend fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean =
        writeMutex.withLock {
            val current = records.get()[key]
            if (current != null && current.matchesAttempt(attemptId)) {
                records.set(records.get() - key)
                true
            } else {
                false
            }
        }

    override fun contains(key: PendingRunSubmissionKey): Boolean = key in records.get()

    override suspend fun clearEndpoint(endpoint: String) {
        writeMutex.withLock {
            records.set(records.get().filterKeys { key -> key.endpoint != endpoint })
        }
    }

    override fun knownRunIds(key: PendingRunSubmissionKey): Set<RunId> = records.get()[key]?.knownRunIds.orEmpty()

    override fun attemptId(key: PendingRunSubmissionKey): String? = records.get()[key]?.attemptId

    override fun snapshot(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? =
        records.get()[key]?.toSnapshot()

    protected fun UncertaintyRecord.matchesAttempt(attemptId: String?): Boolean =
        attemptId == null || this.attemptId == attemptId

    protected suspend fun updateIfMatching(
        key: PendingRunSubmissionKey,
        attemptId: String?,
        transform: (UncertaintyRecord) -> UncertaintyRecord,
    ): Boolean =
        writeMutex.withLock {
            val current = records.get()[key]
            if (current != null && current.matchesAttempt(attemptId)) {
                records.set(records.get() + (key to transform(current)))
                true
            } else {
                false
            }
        }

    protected suspend fun removeIfMatching(
        key: PendingRunSubmissionKey,
        predicate: (UncertaintyRecord) -> Boolean,
    ): Boolean =
        writeMutex.withLock {
            val current = records.get()[key]
            if (current != null && predicate(current)) {
                records.set(records.get() - key)
                true
            } else {
                false
            }
        }
}

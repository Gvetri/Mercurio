package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore

internal const val LEGACY_ATTEMPT_ID = "legacy"

internal sealed class UncertaintyStoreState : RunSubmissionUncertaintyStore {
    protected val lock = Any()
    protected val keys = mutableMapOf<PendingRunSubmissionKey, UncertaintyRecord>()

    override fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean {
        synchronized(lock) {
            val current = keys[key]
            if (current != null && attemptId != null && current.attemptId != attemptId) return false
            val record = current ?: UncertaintyRecord(attemptId = attemptId ?: LEGACY_ATTEMPT_ID)
            record.knownRunIds += knownRunIds
            keys[key] = record
            return true
        }
    }

    override fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean =
        synchronized(lock) {
            val current = keys[key] ?: return false
            if (attemptId != null && current.attemptId != attemptId) return false
            keys.remove(key)
            true
        }

    override fun contains(key: PendingRunSubmissionKey): Boolean = synchronized(lock) { key in keys }

    override fun clearEndpoint(endpoint: String) {
        synchronized(lock) {
            keys.keys.filter { it.endpoint == endpoint }.forEach(keys::remove)
        }
    }

    override fun knownRunIds(key: PendingRunSubmissionKey): Set<RunId> =
        synchronized(lock) {
            keys[key]?.knownRunIds?.toSet().orEmpty()
        }

    override fun attemptId(key: PendingRunSubmissionKey): String? = synchronized(lock) { keys[key]?.attemptId }

    override fun snapshot(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? =
        synchronized(lock) {
            keys[key]?.let { record ->
                RunSubmissionUncertaintySnapshot(
                    attemptId = record.attemptId,
                    knownRunIds = record.knownRunIds.toSet(),
                    boundRunId = record.boundRunId,
                    settled = record.settled,
                    requiresRunMatch = record.requiresRunMatch,
                )
            }
        }

    protected fun UncertaintyRecord.matchesAttempt(attemptId: String?): Boolean =
        attemptId == null || this.attemptId == attemptId
}

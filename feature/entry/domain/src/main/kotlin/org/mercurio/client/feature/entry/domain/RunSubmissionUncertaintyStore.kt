package org.mercurio.client.feature.entry.domain

interface RunSubmissionUncertaintyStore : RunSubmissionUncertaintyReader {
    fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId> = emptySet(),
        attemptId: String? = null,
    ): Boolean

    fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean

    fun contains(key: PendingRunSubmissionKey): Boolean

    fun clearEndpoint(endpoint: String) = Unit

    fun bindRun(
        key: PendingRunSubmissionKey,
        runId: RunId,
        attemptId: String? = null,
    ): Boolean = true

    fun markSettled(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean = true

    fun markAmbiguous(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean = true

    fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String? = null,
    ): Boolean = false

    fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean = false
}

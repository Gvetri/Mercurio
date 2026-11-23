package org.mercurio.client.feature.entry.domain

interface RunSubmissionUncertaintyStore : RunSubmissionUncertaintyReader {
    suspend fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId> = emptySet(),
        attemptId: String? = null,
    ): Boolean

    fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean

    fun contains(key: PendingRunSubmissionKey): Boolean

    suspend fun clearEndpoint(endpoint: String) = Unit

    suspend fun bindRun(
        key: PendingRunSubmissionKey,
        runId: RunId,
        attemptId: String? = null,
    ): Boolean = true

    suspend fun markSettled(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean = true

    suspend fun markAmbiguous(
        key: PendingRunSubmissionKey,
        attemptId: String? = null,
    ): Boolean = true

    suspend fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String? = null,
    ): Boolean = false

    suspend fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean = false
}

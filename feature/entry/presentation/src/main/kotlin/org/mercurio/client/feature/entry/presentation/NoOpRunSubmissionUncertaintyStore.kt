package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore

object NoOpRunSubmissionUncertaintyStore : RunSubmissionUncertaintyStore {
    override fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ) = true

    override fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ) = true

    override fun contains(key: PendingRunSubmissionKey): Boolean = false

    override fun removeIfKnownRunIdsMatch(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean = true

    override fun removeIfSnapshotMatches(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ): Boolean = true
}

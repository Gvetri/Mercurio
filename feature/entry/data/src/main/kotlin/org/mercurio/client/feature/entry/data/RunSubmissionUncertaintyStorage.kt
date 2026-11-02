package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot

interface RunSubmissionUncertaintyStorage {
    fun read(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot?

    fun write(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    )

    fun remove(key: PendingRunSubmissionKey)

    fun clearEndpoint(endpoint: String) = Unit
}

package org.mercurio.client.feature.entry.domain

interface RunSubmissionUncertaintyReader {
    fun knownRunIds(key: PendingRunSubmissionKey): Set<RunId> = emptySet()

    fun attemptId(key: PendingRunSubmissionKey): String? = null

    fun snapshot(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? = null

    fun boundRunId(key: PendingRunSubmissionKey): RunId? = null

    fun isSettled(key: PendingRunSubmissionKey): Boolean = false

    fun requiresRunMatch(key: PendingRunSubmissionKey): Boolean = false
}

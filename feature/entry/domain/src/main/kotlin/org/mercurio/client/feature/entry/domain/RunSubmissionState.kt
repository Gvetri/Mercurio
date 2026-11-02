package org.mercurio.client.feature.entry.domain

data class RunSubmissionState(
    val latestRun: Run? = null,
    val activeRuns: List<Run> = emptyList(),
    val isSubmissionPending: Boolean = false,
) {
    val canSubmit: Boolean
        get() =
            !isSubmissionPending &&
                activeRuns.none(Run::isActive) &&
                latestRun?.isActive() != true
}

package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId

/**
 * The typed seam a test drives [EntryStateHolder] through: every member mirrors a declaration the
 * holder owns and a test must arrange or observe. The seam keeps that reach-out in one declared
 * place with its type attached, instead of a reflective lookup by name whose result the caller has
 * to cast.
 *
 * It is `internal`, so no production caller outside this module reaches it, and it exposes only
 * reads plus the two arrangements a test needs: the state flow it records writes on, and the
 * pending-observation hand-off it makes observable.
 */
internal class EntryStateHolderTestSurface(
    private val holder: EntryStateHolder,
) {
    val scope: CoroutineScope get() = holder.scope
    val mutableUiState: MutableStateFlow<EntryUiState> get() = holder.mutableUiState
    val sessionJob: Job? get() = holder.sessionJob
    val recoveryLoadPending: Boolean get() = holder.recoveryLoadPending
    val recoveryLoadFailed: Boolean get() = holder.recoveryLoadFailed
    val runObservationJobs: MutableMap<SessionId, Job> get() = holder.runObservationJobs
    val runObservationRunIds: MutableMap<SessionId, RunId> get() = holder.runObservationRunIds
    val pendingRunObservationRequests: MutableMap<SessionId, ObserverStartRequest> get() = holder.pendingRunObservationRequests
    val runObservations: MutableMap<SessionId, RunEventObservation> get() = holder.runObservations
    val recoveryUnavailableSessions: MutableSet<SessionId> get() = holder.recoveryUnavailableSessions
    val sessionRuns: MutableMap<SessionId, List<Run>> get() = holder.sessionRuns
    val connectionRecoverySessionCounts: MutableMap<SessionId, Int> get() = holder.connectionRecoverySessionCounts
    val connectionRecoveryJobs: MutableSet<Job> get() = holder.connectionRecoveryJobs
    val authoritativeSessionHistoryGenerations: MutableMap<SessionId, Long> get() =
        holder.authoritativeSessionHistoryGenerations
    val uncertainSubmissionRunIds: MutableMap<SessionId, RunId> get() = holder.uncertainSubmissionRunIds

    fun installUiState(flow: MutableStateFlow<EntryUiState>) {
        holder.mutableUiState = flow
    }

    fun describeInternals(): String =
        listOf(
            "runObservationJobs" to runObservationJobs,
            "runObservationRunIds" to runObservationRunIds,
            "pendingRunObservationRequests" to pendingRunObservationRequests,
            "runObservationStates" to holder.runObservationStates,
            "sessionRuns" to sessionRuns,
            "recoveryLoadPending" to recoveryLoadPending,
            "recoveryLoadFailed" to recoveryLoadFailed,
        ).joinToString(prefix = "{", postfix = "}") { (name, value) -> "$name=$value" }
}

internal val EntryStateHolder.testSurface: EntryStateHolderTestSurface
    get() = EntryStateHolderTestSurface(this)

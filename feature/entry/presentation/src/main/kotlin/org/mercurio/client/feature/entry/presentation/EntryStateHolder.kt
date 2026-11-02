package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.ErrorReporting
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsExporter
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsRecorder
import org.mercurio.client.feature.entry.domain.LocalDiagnosticsStore
import org.mercurio.client.feature.entry.domain.ModelGatewayPort
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.ModelSelectionStore
import org.mercurio.client.feature.entry.domain.NoOpHistoryCacheStore
import org.mercurio.client.feature.entry.domain.NoOpModelSelectionStore
import org.mercurio.client.feature.entry.domain.NoOpSessionDraftStore
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunObservationState
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.RunStatusNotificationPermission
import org.mercurio.client.feature.entry.domain.RunStatusNotificationSettingsStore
import org.mercurio.client.feature.entry.domain.RunStatusNotifier
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.SessionDraftStore
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.toRunPresentationState
import java.util.concurrent.atomic.AtomicBoolean

sealed interface EntryUiEvent {
    data class InboundUri(
        val rawUri: String?,
    ) : EntryUiEvent

    sealed interface Connection : EntryUiEvent

    data object AddGatewayConnectionClicked : Connection

    data object ChangeGatewayCredentialClicked : Connection

    data object CancelGatewayCredentialChangeClicked : Connection

    data class EndpointChanged(
        val value: String,
    ) : Connection

    data class BearerCredentialChanged(
        val value: String,
    ) : Connection

    data object VerifyGatewayConnectionClicked : Connection

    data object TryAgainClicked : Connection

    data object RemoveGatewayConnectionClicked : Connection

    data object ConfirmRemoveGatewayConnectionClicked : Connection

    data object CancelRemoveGatewayConnectionClicked : Connection

    data object RunStatusNotificationsToggleClicked : Connection

    data class RunStatusNotificationPermissionResult(
        val granted: Boolean,
    ) : Connection

    sealed interface SessionNavigation : EntryUiEvent

    data object RefreshSessionsClicked : SessionNavigation

    data object RefreshSessionListClicked : SessionNavigation

    data class SessionSearchQueryChanged(
        val value: String,
    ) : SessionNavigation

    data object ClearSessionSearchClicked : SessionNavigation

    data object LoadMoreSessionsClicked : SessionNavigation

    data class SessionClicked(
        val sessionId: SessionId,
    ) : SessionNavigation

    data object ReturnToSessionListClicked : SessionNavigation

    sealed interface SessionMutation : EntryUiEvent

    data object CreateSessionClicked : SessionMutation

    data class RenameSessionClicked(
        val sessionId: SessionId,
    ) : SessionMutation

    data class RenameSessionTitleChanged(
        val sessionId: SessionId,
        val value: String,
    ) : SessionMutation

    data class ConfirmRenameSessionClicked(
        val sessionId: SessionId,
    ) : SessionMutation

    data class CancelRenameSessionClicked(
        val sessionId: SessionId,
    ) : SessionMutation

    sealed interface SessionPinAndDelete : EntryUiEvent

    data class PinSessionClicked(
        val sessionId: SessionId,
    ) : SessionPinAndDelete

    data class UnpinSessionClicked(
        val sessionId: SessionId,
    ) : SessionPinAndDelete

    data class DeleteSessionClicked(
        val sessionId: SessionId,
    ) : SessionPinAndDelete

    data class ConfirmDeleteSessionClicked(
        val sessionId: SessionId,
    ) : SessionPinAndDelete

    data class CancelDeleteSessionClicked(
        val sessionId: SessionId,
    ) : SessionPinAndDelete

    sealed interface Run : EntryUiEvent

    data class ComposerTextChanged(
        val value: String,
    ) : Run

    data object SendMessageClicked : Run

    data object StopRunClicked : Run

    data object DiscardUnresolvedSendClicked : Run

    data object ConfirmDiscardUnresolvedSendClicked : Run

    data object CancelDiscardUnresolvedSendClicked : Run

    data class RetryRunClicked(
        val runId: RunId,
    ) : Run

    sealed interface ModelPicker : EntryUiEvent

    data object ModelPickerClicked : ModelPicker

    data object ModelPickerDismissed : ModelPicker

    data object ServerDefaultModelClicked : ModelPicker

    data class ModelRowClicked(
        val modelId: String,
    ) : ModelPicker

    data class ModelEffortClicked(
        val effort: String,
    ) : ModelPicker

    data class ModelFavoriteClicked(
        val modelId: String,
        val favorite: Boolean,
    ) : ModelPicker

    sealed interface Settings : EntryUiEvent

    data object OpenSettingsClicked : Settings

    data object CloseSettingsClicked : Settings

    data object ClearHistoryCacheClicked : Settings

    data object ConfirmClearHistoryCacheClicked : Settings

    data object CancelClearHistoryCacheClicked : Settings

    sealed interface Diagnostics : EntryUiEvent

    data object OpenLocalDiagnosticsClicked : Diagnostics

    data object CloseLocalDiagnosticsClicked : Diagnostics

    data object ExportDiagnosticsClicked : Diagnostics

    data object ClearDiagnosticsClicked : Diagnostics

    data object ConfirmClearDiagnosticsClicked : Diagnostics

    data object CancelClearDiagnosticsClicked : Diagnostics
}

enum class EntryErrorCategory(
    val messageRes: Int,
) {
    INVALID_ADDRESS(R.string.error_invalid_address),
    SECURE_CONNECTION_FAILED(R.string.error_secure_connection_failed),
    AUTHENTICATION_FAILED(R.string.error_authentication_failed),
    REQUIRED_FEATURE_UNAVAILABLE(R.string.error_required_feature_unavailable),
    GATEWAY_REQUEST_FAILED(R.string.error_gateway_request_failed),
    CREDENTIAL_STORAGE_FAILED(R.string.error_credential_storage_failed),
}

internal const val DEFAULT_SEND_TIMEOUT_MILLIS = 30_000L
internal const val RECOVERY_CLAIM_ATTEMPTS = 100
internal const val RECOVERY_CLAIM_RETRY_MILLIS = 10L
internal const val UNCERTAIN_RUN_STATUS = "uncertain"
internal const val RECOVERY_PENDING_STATUS = "recovery_pending"

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

data class EntryUiState(
    val title: UiText,
    val supportingText: UiText,
    val actionLabel: UiText,
    val connectionSetupRequested: Boolean = false,
    val isChangingCredential: Boolean = false,
    val endpoint: String = "",
    val bearerCredential: String = "",
    val isVerifying: Boolean = false,
    val isSaving: Boolean = false,
    val isConnected: Boolean = false,
    val errorCategory: EntryErrorCategory? = null,
    val isRemoveConfirmationOpen: Boolean = false,
    val isClearHistoryCacheConfirmationOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val clientVersion: String = "unknown",
    val sessionList: SessionListUiState? = null,
    val runStatusNotifications: RunStatusNotificationsUiState = RunStatusNotificationsUiState(),
    val localDiagnostics: LocalDiagnosticsUiState = LocalDiagnosticsUiState(),
    val isGatewayConnectionConfigured: Boolean = false,
    val inboundLinkNotice: InboundLinkNoticeCategory? = null,
)

/**
 * State of the Local diagnostics surface: a private, redacted buffer of approved
 * operational records with explicit export and clear actions.
 */
data class LocalDiagnosticsUiState(
    val isOpen: Boolean = false,
    val isLoadingRecords: Boolean = false,
    val recordCount: Int = 0,
    val isExporting: Boolean = false,
    val exportFailure: LocalDiagnosticsExportFailure? = null,
    val isClearConfirmationOpen: Boolean = false,
) {
    /** True when the buffer holds records, so an export can produce a snapshot. */
    val hasRecords: Boolean
        get() = recordCount > 0

    /** Export stays disabled until the buffer holds records and no export is running. */
    val isExportEnabled: Boolean
        get() = hasRecords && !isExporting
}

/**
 * The ports the Local diagnostics surface needs, grouped so one buffer collection
 * serves all of them: the recorder appends approved events, the store reports and
 * clears them, and the exporter shares a snapshot of what the buffer holds.
 */
data class LocalDiagnosticsPorts(
    val recorder: LocalDiagnosticsRecorder,
    val store: LocalDiagnosticsStore,
    val exporter: LocalDiagnosticsExporter,
)

/** Recoverable failure shown after an export that produced no snapshot. */
enum class LocalDiagnosticsExportFailure(
    val messageRes: Int,
) {
    EXPORT_FAILED(R.string.error_diagnostics_export_failed),
}

data class RunStatusNotificationsUiState(
    val enabled: Boolean = false,
    val explanation: RunStatusNotificationExplanation? = null,
)

enum class RunStatusNotificationExplanation(
    val messageRes: Int,
) {
    PERMISSION_DENIED(R.string.error_notifications_blocked),
}

internal data class ReleasedConnectionState(
    val jobs: List<Job>,
    val observations: List<RunEventObservation>,
    val pendingCreates: List<PendingCreatePersistence> = emptyList(),
)

data class EntryStateHolderDependencies(
    val runRecoveryRegistry: RunRecoveryRegistry? = null,
    val updateRunRecoveryEndpoint: ((String?) -> Unit)? = null,
    val persistRunRecoveryEntry: ((String, RunRecoveryEntry) -> Unit)? = null,
    val removeRunRecoveryEntry: ((String, RunRecoveryEntry) -> Unit)? = null,
    val removeGatewayConnectionUseCase: RemoveGatewayConnection? = null,
    val runSubmissionUncertaintyStore: RunSubmissionUncertaintyStore = NoOpRunSubmissionUncertaintyStore,
    val sessionDraftStore: SessionDraftStore = NoOpSessionDraftStore,
    val modelSelectionStore: ModelSelectionStore = NoOpModelSelectionStore,
    val historyCacheStore: HistoryCacheStore = NoOpHistoryCacheStore,
    val onRunSubmissionCompleted: (() -> Unit)? = null,
    val onRunSubmissionSettled: (() -> Unit)? = null,
    val requestRunStatusNotificationPermission: (() -> Unit)? = null,
    val runStatusNotificationSettingsStore: RunStatusNotificationSettingsStore? = null,
    val runStatusNotificationPermission: RunStatusNotificationPermission? = null,
    val runStatusNotifier: RunStatusNotifier? = null,
    val localDiagnostics: LocalDiagnosticsPorts? = null,
    val errorReporting: ErrorReporting? = null,
    val sendTimeoutMillis: Long = DEFAULT_SEND_TIMEOUT_MILLIS,
)

class EntryStateHolder(
    initialState: EntryState,
    internal val verifyGatewayConnection: VerifyGatewayConnection? = null,
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    internal val sessionGatewayFactory: ((endpoint: String, bearerCredential: String) -> SessionGatewayPort)? = null,
    internal val runGatewayFactory: ((endpoint: String, bearerCredential: String) -> RunGatewayPort)? = null,
    dependencies: EntryStateHolderDependencies = EntryStateHolderDependencies(),
) {
    internal val runRecoveryRegistry = dependencies.runRecoveryRegistry
    internal val updateRunRecoveryEndpoint = dependencies.updateRunRecoveryEndpoint
    internal val persistRunRecoveryEntry = dependencies.persistRunRecoveryEntry
    internal val removeRunRecoveryEntry = dependencies.removeRunRecoveryEntry
    internal val removeGatewayConnectionUseCase = dependencies.removeGatewayConnectionUseCase
    internal val runSubmissionUncertaintyStore = dependencies.runSubmissionUncertaintyStore
    internal val sessionDraftStore = dependencies.sessionDraftStore
    internal val modelSelectionStore = dependencies.modelSelectionStore
    internal val historyCacheStore = dependencies.historyCacheStore
    internal val onRunSubmissionCompleted = dependencies.onRunSubmissionCompleted
    internal val onRunSubmissionSettled = dependencies.onRunSubmissionSettled
    internal val requestRunStatusNotificationPermission = dependencies.requestRunStatusNotificationPermission
    internal val runStatusNotificationSettingsStore = dependencies.runStatusNotificationSettingsStore
    internal val runStatusNotificationPermission = dependencies.runStatusNotificationPermission
    internal val runStatusNotifier = dependencies.runStatusNotifier
    internal val localDiagnostics = dependencies.localDiagnostics
    internal val errorReporting = dependencies.errorReporting
    internal val sendTimeoutMillis = dependencies.sendTimeoutMillis

    init {
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be positive." }
    }

    internal val mutableUiState =
        MutableStateFlow(
            initialState.toUiState().copy(runStatusNotifications = restoredRunStatusNotificationState()),
        )
    val uiState: StateFlow<EntryUiState> = mutableUiState.asStateFlow()

    internal val persistenceScope: CoroutineScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    internal var verificationJob: Job? = null

    @Volatile
    internal var pendingRemovalJob: Job? = null
    internal var sessionJob: Job? = null
    internal var recoveryJob: Job? = null
    internal var sessionGateway: SessionGatewayPort? = null
    internal var runGateway: RunGatewayPort? = null
    internal var catalogGateway: ModelGatewayPort? = null
    internal val sessionRequestLock = Any()
    internal val recoveryPersistenceLock = Any()
    internal val connectionPersistenceLock = Any()
    internal val localDiagnosticsClearInFlight = AtomicBoolean(false)
    internal var sessionRequestGeneration = 0L
    internal var connectionGeneration = 0L
    internal val mutationJobs = mutableMapOf<SessionId, Job>()
    internal var nextMutationAttemptId = 0L
    internal val mutationOwners = mutableMapOf<SessionId, SessionMutationRequest>()
    internal val runJobs = mutableMapOf<SessionId, Job>()
    internal val sessionDrafts = mutableMapOf<SessionId, String>()
    internal val sessionDraftRevisions = mutableMapOf<SessionId, Long>()
    internal val sessionSendErrors = mutableMapOf<SessionId, MessageSendErrorCategory>()
    internal val pendingRunDrafts = mutableMapOf<SessionId, PendingDraft>()
    internal val pendingDisconnectedRecoveryEntries = mutableMapOf<String, MutableSet<RunRecoveryEntry>>()
    internal val connectionRecoveryJobs = mutableSetOf<Job>()
    internal val connectionRecoverySessionCounts = mutableMapOf<SessionId, Int>()
    internal val connectionRecoveryFailedSessions = mutableSetOf<SessionId>()
    internal val recoverySessionCounts = mutableMapOf<SessionId, Int>()
    internal var nextRecoveryClaimId = 0L
    internal var nextComposerFocusRequestId = 0L
    internal var inboundLinkListRequestInterrupted = false
    internal val recoveryClaims = mutableMapOf<RecoveryEntryKey, RecoveryClaim>()
    internal val recoveryHandledEntries = mutableSetOf<RecoveryEntryKey>()
    internal var recoveryLoadPending = false
    internal var recoveryLoadFailed = false
    internal val pendingCreateSessions = mutableMapOf<RecoverySessionKey, PendingCreateState>()
    internal val pendingCreateStarted = mutableSetOf<RecoverySessionKey>()

    internal val sessionRuns = mutableMapOf<SessionId, List<Run>>()
    internal val submittedRunInputs = mutableMapOf<RunId, String>()
    internal val submittedRunModelSelections = mutableMapOf<RunId, ModelSelection>()
    internal val unresolvedLocalRunIds = mutableMapOf<RecoverySessionKey, MutableSet<RunId>>()
    internal val unresolvedLocalRunAttempts = mutableMapOf<RecoverySessionKey, MutableMap<RunId, String>>()
    internal val authoritativeSessionHistoryGenerations = mutableMapOf<SessionId, Long>()
    internal val authoritativeSessionRuns = mutableMapOf<SessionId, List<Run>>()
    internal val runObservationJobs = mutableMapOf<SessionId, Job>()
    internal val runObservations = mutableMapOf<SessionId, RunEventObservation>()
    internal val runObservationRunIds = mutableMapOf<SessionId, RunId>()
    internal val pendingRunObservationRequests = mutableMapOf<SessionId, ObserverStartRequest>()
    internal val runObservationStates = mutableMapOf<SessionId, MutableMap<RunId, RunObservationState>>()
    internal val unresolvedSubmissionSessions = mutableSetOf<SessionId>()
    internal val ambiguousSubmissionSessions = mutableSetOf<SessionId>()
    internal val recoveryUnavailableSessions = mutableSetOf<SessionId>()
    internal val unresolvedSessionMutations = mutableMapOf<RecoverySessionKey, SessionMutationUiState>()
    internal val uncertainSendDrafts = mutableMapOf<SessionId, PendingDraft>()
    internal val uncertainSubmissionRunIds = mutableMapOf<SessionId, RunId>()
    internal val uncertainSubmissionAttemptIds = mutableMapOf<SessionId, String>()
    internal val pendingTimedOutSends = mutableMapOf<SessionId, TimedOutSendRecovery>()
    internal val reconcilingSessions = mutableMapOf<SessionId, Long>()
    internal val notifiedTerminalRunIds = mutableSetOf<RunId>()

    internal val stateChangeCommands = Channel<StateChangeCommand>(Channel.BUFFERED)

    init {
        scope.launch(StateChangeConsumerMarker()) { consumeStateChanges() }
    }

    fun onEvent(event: EntryUiEvent) {
        when (event) {
            is EntryUiEvent.InboundUri -> handleInboundUri(event.rawUri)
            is EntryUiEvent.Connection -> dispatchConnectionEvent(event)
            is EntryUiEvent.SessionNavigation -> dispatchSessionNavigationEvent(event)
            is EntryUiEvent.SessionMutation -> dispatchSessionMutationEvent(event)
            is EntryUiEvent.SessionPinAndDelete -> dispatchSessionPinDeleteEvent(event)
            is EntryUiEvent.Run -> dispatchRunEvent(event)
            is EntryUiEvent.ModelPicker -> scope.launch { handleModelPickerEvent(event) }
            is EntryUiEvent.Settings -> dispatchSettingsEvent(event)
            is EntryUiEvent.Diagnostics -> dispatchDiagnosticsEvent(event)
        }
    }

    private fun dispatchConnectionEvent(event: EntryUiEvent.Connection) {
        when (event) {
            is EntryUiEvent.AddGatewayConnectionClicked -> showConnectionSetup()
            is EntryUiEvent.ChangeGatewayCredentialClicked -> showCredentialRotation()
            is EntryUiEvent.CancelGatewayCredentialChangeClicked -> cancelCredentialRotation()
            is EntryUiEvent.EndpointChanged -> updateEndpoint(event.value)
            is EntryUiEvent.BearerCredentialChanged -> updateBearerCredential(event.value)
            is EntryUiEvent.VerifyGatewayConnectionClicked -> verifyConnection()
            is EntryUiEvent.TryAgainClicked -> verifyConnection()
            is EntryUiEvent.RemoveGatewayConnectionClicked -> requestRemoveGatewayConnection()
            is EntryUiEvent.ConfirmRemoveGatewayConnectionClicked -> confirmRemoveGatewayConnection()
            is EntryUiEvent.CancelRemoveGatewayConnectionClicked -> cancelRemoveGatewayConnection()
            is EntryUiEvent.RunStatusNotificationsToggleClicked -> toggleRunStatusNotifications()
            is EntryUiEvent.RunStatusNotificationPermissionResult ->
                applyRunStatusNotificationPermissionResult(event.granted)
        }
    }

    private fun dispatchSessionNavigationEvent(event: EntryUiEvent.SessionNavigation) {
        when (event) {
            is EntryUiEvent.RefreshSessionsClicked -> refreshSessions()
            is EntryUiEvent.RefreshSessionListClicked -> refreshSessionList()
            is EntryUiEvent.SessionSearchQueryChanged -> updateSearchQuery(event.value)
            is EntryUiEvent.ClearSessionSearchClicked -> clearSearch()
            is EntryUiEvent.LoadMoreSessionsClicked -> loadMoreSessions()
            is EntryUiEvent.SessionClicked -> openSession(event.sessionId)
            is EntryUiEvent.ReturnToSessionListClicked -> returnToSessionList()
        }
    }

    private fun dispatchSessionMutationEvent(event: EntryUiEvent.SessionMutation) {
        when (event) {
            is EntryUiEvent.CreateSessionClicked -> showCreateSession()
            is EntryUiEvent.RenameSessionClicked -> showRenameSession(event.sessionId)
            is EntryUiEvent.RenameSessionTitleChanged -> updateRenameSessionTitle(event.sessionId, event.value)
            is EntryUiEvent.ConfirmRenameSessionClicked -> confirmRenameSession(event.sessionId)
            is EntryUiEvent.CancelRenameSessionClicked -> cancelRenameSession(event.sessionId)
        }
    }

    private fun dispatchSessionPinDeleteEvent(event: EntryUiEvent.SessionPinAndDelete) {
        when (event) {
            is EntryUiEvent.PinSessionClicked -> pinSession(event.sessionId)
            is EntryUiEvent.UnpinSessionClicked -> unpinSession(event.sessionId)
            is EntryUiEvent.DeleteSessionClicked -> showDeleteSession(event.sessionId)
            is EntryUiEvent.ConfirmDeleteSessionClicked -> confirmDeleteSession(event.sessionId)
            is EntryUiEvent.CancelDeleteSessionClicked -> cancelDeleteSession(event.sessionId)
        }
    }

    private fun dispatchRunEvent(event: EntryUiEvent.Run) {
        when (event) {
            is EntryUiEvent.ComposerTextChanged -> updateComposerText(event.value)
            is EntryUiEvent.SendMessageClicked -> dispatchSendMessage()
            is EntryUiEvent.StopRunClicked -> stopRunObservation()
            is EntryUiEvent.DiscardUnresolvedSendClicked -> showDiscardUnresolvedSendConfirmation()
            is EntryUiEvent.ConfirmDiscardUnresolvedSendClicked -> confirmDiscardUnresolvedSend()
            is EntryUiEvent.CancelDiscardUnresolvedSendClicked -> cancelDiscardUnresolvedSend()
            is EntryUiEvent.RetryRunClicked -> retryRun(event.runId)
        }
    }

    private fun dispatchSettingsEvent(event: EntryUiEvent.Settings) {
        when (event) {
            is EntryUiEvent.OpenSettingsClicked -> openSettings()
            is EntryUiEvent.CloseSettingsClicked -> closeSettings()
            is EntryUiEvent.ClearHistoryCacheClicked -> requestClearHistoryCache()
            is EntryUiEvent.ConfirmClearHistoryCacheClicked -> confirmClearHistoryCache()
            is EntryUiEvent.CancelClearHistoryCacheClicked -> cancelClearHistoryCache()
        }
    }

    private fun dispatchDiagnosticsEvent(event: EntryUiEvent.Diagnostics) {
        when (event) {
            is EntryUiEvent.OpenLocalDiagnosticsClicked -> openLocalDiagnostics()
            is EntryUiEvent.CloseLocalDiagnosticsClicked -> closeLocalDiagnostics()
            is EntryUiEvent.ExportDiagnosticsClicked -> exportLocalDiagnostics()
            is EntryUiEvent.ClearDiagnosticsClicked -> requestClearLocalDiagnostics()
            is EntryUiEvent.ConfirmClearDiagnosticsClicked -> confirmClearLocalDiagnostics()
            is EntryUiEvent.CancelClearDiagnosticsClicked -> cancelClearLocalDiagnostics()
        }
    }

    internal fun OpenSessionUiState.withRunSnapshot(
        knownRuns: List<Run>,
        latestObservation: RunObservationState?,
    ): OpenSessionUiState {
        val latestRunState = latestObservation?.state ?: knownRuns.latestRun()?.toRunPresentationState()
        return copy(
            latestRun = knownRuns.latestRun(),
            activeRuns = knownRuns.activeRuns(),
            latestRunState = latestRunState,
            latestRunRetryAvailable = latestRunRetryAvailable(knownRuns, latestRunState),
            activeResponse = observedMessageUiState(latestObservation),
        )
    }

    /**
     * Releases the connection state and clears the removal confirmation so a closed holder never
     * reports an open dialog.
     */
    fun close() {
        mutableUiState.value = mutableUiState.value.copy(isRemoveConfirmationOpen = false)
        val released = releaseConnectionState()
        released.jobs.forEach(Job::cancel)
        released.observations.forEach(RunEventObservation::close)
        persistPendingCreatesOffThread(released.pendingCreates)
        scope.cancel()
    }
}

internal data class OpenSessionOverlay(
    val composerText: String = "",
    val sendErrorCategory: MessageSendErrorCategory? = null,
    val hasUnresolvedSubmission: Boolean = false,
    val latestRun: Run? = null,
    val activeRuns: List<Run>? = null,
    val isSending: Boolean = false,
    val latestRunState: RunPresentationState? = null,
    val latestRunRetryAvailable: Boolean = false,
    val activeResponse: SessionMessageUiState? = null,
    val isRefreshing: Boolean = false,
    val messages: List<SessionMessageUiState>? = null,
)

internal data class RecoveryRunContext(
    val sessionGateway: SessionGatewayPort,
    val runGateway: RunGatewayPort,
    val connectionGeneration: Long,
    val historyGeneration: Long,
    val sessionGeneration: Long? = null,
    val updateVisibleUi: Boolean = false,
)

internal data class RecoveryGatewayContext(
    val endpoint: String,
    val sessionGateway: SessionGatewayPort,
    val runGateway: RunGatewayPort,
)

internal data class SessionMutationRequest(
    val sessionId: SessionId,
    val gateway: SessionGatewayPort,
    val connectionGeneration: Long,
    val attemptId: Long,
)

internal data class RecoveryEntryKey(
    val endpoint: String,
    val sessionId: SessionId,
    val runId: RunId,
)

internal data class RecoveryClaim(
    val key: RecoveryEntryKey,
    val connectionGeneration: Long,
    val claimId: Long,
)

internal data class RecoveryWorkItem(
    val entry: RunRecoveryEntry,
    val claim: RecoveryClaim,
)

internal data class RecoverySessionKey(
    val endpoint: String,
    val sessionId: SessionId,
)

internal data class PendingCreateState(
    val knownRunIds: Set<RunId>,
    val attemptId: String,
)

internal data class PendingDraft(
    val text: String,
    val revision: Long,
)

internal data class ObserverStartRequest(
    val run: Run,
    val connectionGeneration: Long,
    val sessionGeneration: Long,
)

internal data class ReconciliationRequest(
    val connectionGeneration: Long,
    val sessionGeneration: Long,
)

internal data class ReconciledObservationRelease(
    val job: Job? = null,
    val observation: RunEventObservation? = null,
    val wasActivelyObserved: Boolean = false,
)

internal data class OpenedRunReconcileContext(
    val sessionId: SessionId,
    val requestSessionGeneration: Long,
    val requestConnectionGeneration: Long,
    val sessionGateway: SessionGatewayPort,
    val runIdToReconcile: RunId? = null,
    val restartObservation: Boolean = false,
    val clearRefreshWhenNoRun: Boolean = false,
)

internal data class RunSubmissionWork(
    val gateway: RunGatewayPort,
    val input: String,
    val rejectRunId: RunId?,
    val sessionId: SessionId,
    val requestConnectionGeneration: Long,
    val requestEndpoint: String,
    val recoverySessionKey: RecoverySessionKey,
    val pendingCreate: PendingCreateState,
    val attemptId: String,
    val knownRunIds: Set<RunId>,
    val selection: ModelSelection?,
    val recordDraft: Boolean,
)

/**
 * A launched model-catalog fetch bound to the connection it was started for; a result is
 * applied only while that connection is still current.
 */
internal data class ModelCatalogFetchRequest(
    val port: ModelGatewayPort,
    val endpoint: String,
    val connectionGeneration: Long,
)

internal data class SubmittedRunApplication(
    val sessionId: SessionId,
    val run: Run,
    val requestConnectionGeneration: Long,
    val requestEndpoint: String,
    val attemptId: String,
    val pendingKey: PendingRunSubmissionKey,
    val submissionBindingSucceeded: Boolean,
    val recoveryPersistenceFailed: Boolean,
)

internal data class SubmittedRunPreparation(
    val pendingKey: PendingRunSubmissionKey,
    val submissionBindingSucceeded: Boolean,
    val persistBeforeApply: Boolean,
    val recoveryPersistenceFailed: Boolean,
)

internal data class AuthoritativeReconciliationRequest(
    val sessionId: SessionId,
    val connectionGeneration: Long,
    val sessionGeneration: Long?,
    val historyGeneration: Long? = null,
    val updateVisibleUi: Boolean = true,
)

internal typealias RunRecoveryStart = Pair<RunRecoveryRegistry, RecoveryGatewayContext>

internal data class ReconciledRunApplication(
    val observationJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
    val recoveryEntryToRemove: RunRecoveryEntry? = null,
    val recoveryEndpointToRemove: String? = null,
)

internal data class SubmittedRunApplicationResult(
    val applied: Boolean,
    val persistAfterDisconnect: Boolean = false,
    val requestSessionGeneration: Long = 0L,
    val shouldObserve: Boolean = false,
    val observationJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
)

internal data class RunObservationStart(
    val jobToStart: Job? = null,
    val observerJobToCancel: Job? = null,
    val observationToClose: RunEventObservation? = null,
    val handoffRequested: Boolean = false,
)

internal data class TimedOutSendAttempt(
    val sessionId: SessionId,
    val gateways: Pair<SessionGatewayPort, RunGatewayPort>,
    val knownRunIds: Set<RunId>,
    val requestEndpoint: String,
    val request: ReconciliationRequest,
    val resolveWhenNoNewRun: Boolean,
)

internal data class TimedOutSendUncertainty(
    val pendingKey: PendingRunSubmissionKey,
    val pendingRecovery: TimedOutSendRecovery?,
    val recoveryDraft: PendingDraft?,
    val attemptId: String,
    val authoritativeMessages: List<SessionMessageUiState>,
    val retainedAuthoritativeRuns: List<Run>,
    val authoritativeRuns: List<Run>,
    val durableBoundRunId: RunId?,
    val discoveredUnattributedRun: Boolean,
    val boundSubmissionRun: Run?,
    val discoveredLocalRun: Run?,
    val submissionRun: Run?,
    val terminalRunIds: Set<RunId>,
    val canClearUncertainty: Boolean,
)

internal data class TimedOutSendRecovery(
    val knownRunIds: Set<RunId>,
    val attemptId: String,
    val draft: PendingDraft,
    val errorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
)

internal data class TimedOutSendUiContext(
    val current: SessionListUiState,
    val opened: OpenSessionUiState,
    val recoveryDraft: PendingDraft? = null,
    val authoritativeMessages: List<SessionMessageUiState>? = null,
)

internal data class TimedOutSendReconciliationRequest(
    val sessionId: SessionId,
    val connectionGeneration: Long,
    val knownRunIds: Set<RunId>,
    val attemptId: String,
    val resolveWhenNoNewRun: Boolean = false,
    val uncertaintyErrorCategory: MessageSendErrorCategory = MessageSendErrorCategory.UNCERTAIN,
)

internal data class TimedOutSendReconciliationOutcome(
    val applied: Boolean,
    val runToObserve: Run? = null,
    val runToPersist: RunRecoveryEntry? = null,
    val runToRemove: RunRecoveryEntry? = null,
)

internal data class SessionRequestContext(
    val generation: Long,
    val query: String,
    val offset: Int?,
    val linked: Boolean = false,
)

internal data class CreateSessionRequest(
    val context: SessionRequestContext,
    val title: String?,
)

internal typealias SessionMutationMap = Map<SessionId, SessionMutationUiState>

internal fun EntryState.toUiState(): EntryUiState =
    if (isGatewayConnectionConfigured || configuredEndpoint != null) {
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_saved),
            supportingText = UiText.Resource(R.string.entry_supporting_verify_saved),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = configuredEndpoint.orEmpty(),
            bearerCredential = configuredCredential.orEmpty(),
            isGatewayConnectionConfigured = true,
            clientVersion = clientVersion,
        )
    } else {
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connect),
            supportingText = UiText.Resource(R.string.entry_supporting_connect),
            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
            clientVersion = clientVersion,
        )
    }

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
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.ModelGatewayPort
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunObservationState
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.toRunPresentationState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
    internal val imageLoadingSettingsStore = dependencies.imageLoadingSettingsStore
    internal val runStatusNotificationPermission = dependencies.runStatusNotificationPermission
    internal val runStatusNotifier = dependencies.runStatusNotifier
    internal val localDiagnostics = dependencies.localDiagnostics
    internal val errorReporting = dependencies.errorReporting
    internal val errorReportingConsentStore = dependencies.errorReportingConsentStore
    internal val isErrorReportingConfigured = dependencies.isErrorReportingConfigured
    internal val appLock = dependencies.appLock
    internal val checkForNightlyUpdate = dependencies.checkForNightlyUpdate
    internal val downloadNightlyUpdate = dependencies.downloadNightlyUpdate
    internal val installNightlyUpdate = dependencies.installNightlyUpdate
    internal val requestInstallPermissionSettings = dependencies.requestInstallPermissionSettings
    internal val requestAppRestart = dependencies.requestAppRestart
    internal val sendTimeoutMillis = dependencies.sendTimeoutMillis
    internal val stateChangeDispatcher = dependencies.stateChangeDispatcher

    private val storedErrorReportingConsent: ErrorReportingConsent? = errorReportingConsentStore?.read()

    init {
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be positive." }
    }

    internal val mutableUiState =
        MutableStateFlow(
            initialState
                .toUiState()
                .copy(
                    runStatusNotifications = restoredRunStatusNotificationState(),
                    autoLoadImages = imageLoadingSettingsStore?.loadEnabled() ?: true,
                    isErrorReportingConsentRequired = storedErrorReportingConsent == ErrorReportingConsent.Undecided,
                    errorReportingConsent = storedErrorReportingConsent ?: ErrorReportingConsent.Undecided,
                    isErrorReportingConfigured = isErrorReportingConfigured,
                    appLock = restoredAppLockState(),
                ),
        )
    val uiState: StateFlow<EntryUiState> = mutableUiState.asStateFlow()

    internal val persistenceScope: CoroutineScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    internal var verificationJob: Job? = null
    internal var updateCheckJob: Job? = null
    internal var updateDownloadJob: Job? = null
    internal var updateDownloadOffer: NightlyUpdateOffer? = null
    internal var updateInstallArtifact: NightlyArtifact? = null

    @Volatile
    internal var updateDownloadGeneration = 0L

    @Volatile
    internal var updateCheckGeneration = 0L
    internal var credentialBeforeRotation: String? = null

    @Volatile
    internal var pendingRemovalJob: Job? = null
    internal var sessionJob: Job? = null
    internal var recoveryJob: Job? = null
    internal var sessionGateway: SessionGatewayPort? = null
    internal var runGateway: RunGatewayPort? = null
    internal var catalogGateway: ModelGatewayPort? = null
    internal val localDiagnosticsClearInFlight = AtomicBoolean(false)
    internal var sessionRequestGeneration = 0L
    internal var connectionGeneration = 0L
    internal val mutationJobs = ConcurrentHashMap<SessionId, Job>()
    internal var nextMutationAttemptId = 0L
    internal val mutationOwners = ConcurrentHashMap<SessionId, SessionMutationRequest>()
    internal val runJobs = ConcurrentHashMap<SessionId, Job>()
    internal val sessionDrafts = ConcurrentHashMap<SessionId, String>()
    internal var pendingSharedText: String? = null
    internal val sessionDraftRevisions = ConcurrentHashMap<SessionId, Long>()
    internal val sessionSendErrors = mutableMapOf<SessionId, MessageSendErrorCategory>()
    internal val pendingRunDrafts = mutableMapOf<SessionId, PendingDraft>()
    internal val pendingDisconnectedRecoveryEntries = ConcurrentHashMap<String, MutableSet<RunRecoveryEntry>>()
    internal val connectionRecoveryJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    internal val connectionRecoverySessionCounts: MutableMap<SessionId, Int> = ConcurrentHashMap()
    internal val connectionRecoveryFailedSessions: MutableSet<SessionId> = ConcurrentHashMap.newKeySet()
    internal val recoverySessionCounts = ConcurrentHashMap<SessionId, Int>()
    internal var nextRecoveryClaimId = 0L
    internal var nextComposerFocusRequestId = 0L
    internal var inboundLinkListRequestInterrupted = false
    internal var isForeground = false
    internal var isUnlockPromptPending = false
    internal val recoveryClaims = ConcurrentHashMap<RecoveryEntryKey, RecoveryClaim>()
    internal val recoveryReconciledRunIds: MutableSet<RunId> = ConcurrentHashMap.newKeySet()
    internal val recoveryHandledEntries: MutableSet<RecoveryEntryKey> = ConcurrentHashMap.newKeySet()
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
    internal val pendingTimedOutSends = ConcurrentHashMap<SessionId, TimedOutSendRecovery>()
    internal val reconcilingSessions = mutableMapOf<SessionId, Long>()
    internal val notifiedTerminalRunIds = mutableSetOf<RunId>()

    internal val stateChangeCommands = Channel<StateChangeCommand>(Channel.UNLIMITED)

    init {
        val marker = StateChangeConsumerMarker()
        val stateChangeContext = stateChangeDispatcher?.let { dispatcher -> marker + dispatcher } ?: marker
        scope.launch(stateChangeContext) { consumeStateChanges() }
    }

    /**
     * Enqueues the event on the state-change consumer. The consumer applies the event in order
     * with every other queued state change, so UI events cannot race an in-flight asynchronous
     * result application.
     */
    fun onEvent(event: EntryUiEvent) {
        submitStateChange {
            when (event) {
                is EntryUiEvent.Intake -> dispatchIntakeEvent(event)
                is EntryUiEvent.Connection -> dispatchConnectionEvent(event)
                is EntryUiEvent.SessionNavigation -> dispatchSessionNavigationEvent(event)
                is EntryUiEvent.SessionMutation -> dispatchSessionMutationEvent(event)
                is EntryUiEvent.SessionPinAndDelete -> dispatchSessionPinDeleteEvent(event)
                is EntryUiEvent.Run -> dispatchRunEvent(event)
                is EntryUiEvent.ModelPicker -> handleModelPickerEvent(event)
                is EntryUiEvent.AppLock -> dispatchAppLockEvent(event)
                is EntryUiEvent.Settings -> dispatchSettingsEvent(event)
                is EntryUiEvent.Diagnostics -> dispatchDiagnosticsEvent(event)
                is EntryUiEvent.ErrorReportingConsentEvent ->
                    recordErrorReportingConsent(event.decision)
                is EntryUiEvent.AppLifecycle -> applyAppLifecycleEvent(event)
            }
        }
    }

    private fun dispatchConnectionEvent(event: EntryUiEvent.Connection) {
        when (event) {
            is EntryUiEvent.AddGatewayConnectionClicked -> showConnectionSetup()
            is EntryUiEvent.ChangeGatewayCredentialClicked -> showCredentialRotation()
            is EntryUiEvent.CancelGatewayCredentialChangeClicked -> cancelCredentialRotation()
            is EntryUiEvent.CloseGatewayConnectionSetupClicked -> closeGatewayConnectionSetup()
            is EntryUiEvent.EndpointChanged -> updateEndpoint(event.value)
            is EntryUiEvent.BearerCredentialChanged -> updateBearerCredential(event.value)
            is EntryUiEvent.VerifyGatewayConnectionClicked,
            is EntryUiEvent.TryAgainClicked,
            -> verifyConnection()
            is EntryUiEvent.RemoveGatewayConnectionClicked -> requestRemoveGatewayConnection()
            is EntryUiEvent.ConfirmRemoveGatewayConnectionClicked -> confirmRemoveGatewayConnection()
            is EntryUiEvent.CancelRemoveGatewayConnectionClicked -> cancelRemoveGatewayConnection()
            is EntryUiEvent.RunStatusNotificationsToggleClicked -> toggleRunStatusNotifications()
            is EntryUiEvent.AutoLoadImagesToggleClicked -> toggleAutoLoadImages()
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

    private suspend fun dispatchRunEvent(event: EntryUiEvent.Run) {
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

    private suspend fun dispatchSettingsEvent(event: EntryUiEvent.Settings) {
        when (event) {
            is EntryUiEvent.OpenSettingsClicked -> openSettings()
            is EntryUiEvent.CheckForUpdatesClicked -> checkForUpdates()
            is EntryUiEvent.DownloadUpdateClicked -> downloadUpdate()
            is EntryUiEvent.CancelUpdateDownloadClicked -> cancelUpdateDownload()
            is EntryUiEvent.OpenInstallPermissionSettingsClicked -> openInstallPermissionSettings()
            is EntryUiEvent.RestartToFinishClicked -> restartToFinish()
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
     * Enqueues the holder teardown behind the commands already queued. The consumer applies
     * every event enqueued before this call, then releases the connection state, clears the
     * removal confirmation so a closed holder never reports an open dialog, and cancels the
     * holder scope.
     */
    fun close() {
        submitStateChange { releaseForClose() }
    }

    private fun releaseForClose() {
        mutableUiState.value = mutableUiState.value.copy(isRemoveConfirmationOpen = false)
        val released = releaseConnectionState()
        released.jobs.forEach(Job::cancel)
        released.observations.forEach(RunEventObservation::close)
        persistPendingCreatesOffThread(released.pendingCreates)
        scope.cancel()
    }
}

package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import org.mercurio.client.feature.entry.application.CheckForNightlyUpdate
import org.mercurio.client.feature.entry.application.DownloadNightlyUpdate
import org.mercurio.client.feature.entry.application.ErrorReporting
import org.mercurio.client.feature.entry.application.InstallNightlyUpdate
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.ImageLoadingSettingsStore
import org.mercurio.client.feature.entry.domain.ModelSelectionStore
import org.mercurio.client.feature.entry.domain.NoOpHistoryCacheStore
import org.mercurio.client.feature.entry.domain.NoOpModelSelectionStore
import org.mercurio.client.feature.entry.domain.NoOpSessionDraftStore
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.RunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.RunStatusNotificationPermission
import org.mercurio.client.feature.entry.domain.RunStatusNotificationSettingsStore
import org.mercurio.client.feature.entry.domain.RunStatusNotifier
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.SessionDraftStore

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
    val imageLoadingSettingsStore: ImageLoadingSettingsStore? = null,
    val runStatusNotificationPermission: RunStatusNotificationPermission? = null,
    val runStatusNotifier: RunStatusNotifier? = null,
    val localDiagnostics: LocalDiagnosticsPorts? = null,
    val errorReporting: ErrorReporting? = null,
    val errorReportingConsentStore: ErrorReportingConsentStore? = null,
    val isErrorReportingConfigured: Boolean = false,
    val appLock: AppLockPorts? = null,
    val checkForNightlyUpdate: CheckForNightlyUpdate? = null,
    val downloadNightlyUpdate: DownloadNightlyUpdate? = null,
    val installNightlyUpdate: InstallNightlyUpdate? = null,
    val requestInstallPermissionSettings: (() -> Unit)? = null,
    val requestAppRestart: (() -> Unit)? = null,
    val sendTimeoutMillis: Long = DEFAULT_SEND_TIMEOUT_MILLIS,
    val stateChangeDispatcher: CoroutineDispatcher? = null,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.InstallSource

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
    val buildNumber: Long? = null,
    val installSource: InstallSource = InstallSource.OTHER_SOURCE,
    val appUpdates: AppUpdatesUiState = AppUpdatesUiState(),
    val sessionList: SessionListUiState? = null,
    val runStatusNotifications: RunStatusNotificationsUiState = RunStatusNotificationsUiState(),
    val autoLoadImages: Boolean = true,
    val localDiagnostics: LocalDiagnosticsUiState = LocalDiagnosticsUiState(),
    val isGatewayConnectionConfigured: Boolean = false,
    val gatewayFacts: UiText? = null,
    val inboundLinkNotice: InboundLinkNoticeCategory? = null,
    val isErrorReportingConsentRequired: Boolean = false,
    val errorReportingConsent: ErrorReportingConsent = ErrorReportingConsent.Undecided,
    val isErrorReportingConfigured: Boolean = false,
    val appLock: AppLockUiState = AppLockUiState(),
)

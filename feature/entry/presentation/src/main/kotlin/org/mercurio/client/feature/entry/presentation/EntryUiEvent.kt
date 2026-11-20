package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId

sealed interface EntryUiEvent {
    sealed interface Intake : EntryUiEvent

    data class InboundUri(
        val rawUri: String?,
    ) : Intake

    data class SharedText(
        val mimeType: String?,
        val text: String?,
        val hasStream: Boolean,
        val isMultiple: Boolean,
    ) : Intake

    data object SharedTextDismissed : Intake

    sealed interface Connection : EntryUiEvent

    data object AddGatewayConnectionClicked : Connection

    data object ChangeGatewayCredentialClicked : Connection

    data object CancelGatewayCredentialChangeClicked : Connection

    data object CloseGatewayConnectionSetupClicked : Connection

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

    data object AutoLoadImagesToggleClicked : Connection

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

    sealed interface AppLock : EntryUiEvent

    data object AppLockToggleClicked : AppLock

    data object AppUnlockRequested : AppLock

    data class AppLockAuthenticationResult(
        val authenticated: Boolean,
    ) : AppLock

    sealed interface Settings : EntryUiEvent

    data object OpenSettingsClicked : Settings

    data object CheckForUpdatesClicked : Settings

    data object DownloadUpdateClicked : Settings

    data object CancelUpdateDownloadClicked : Settings

    data object OpenInstallPermissionSettingsClicked : Settings

    data object RestartToFinishClicked : Settings

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

    sealed interface ErrorReportingConsentEvent : EntryUiEvent {
        val decision: ErrorReportingConsent
    }

    data object ErrorReportingConsentGranted : ErrorReportingConsentEvent {
        override val decision: ErrorReportingConsent = ErrorReportingConsent.Granted
    }

    data object ErrorReportingConsentDeclined : ErrorReportingConsentEvent {
        override val decision: ErrorReportingConsent = ErrorReportingConsent.Declined
    }

    data class ErrorReportingConsentToggleClicked(
        val granted: Boolean,
    ) : ErrorReportingConsentEvent {
        override val decision: ErrorReportingConsent
            get() = if (granted) ErrorReportingConsent.Granted else ErrorReportingConsent.Declined
    }

    sealed interface AppLifecycle : EntryUiEvent

    data object AppEnteredForeground : AppLifecycle

    data object AppEnteredBackground : AppLifecycle
}

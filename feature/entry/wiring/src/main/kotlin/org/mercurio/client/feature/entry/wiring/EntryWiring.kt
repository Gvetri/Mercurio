package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.application.ErrorReporting
import org.mercurio.client.feature.entry.application.LoadEntryState
import org.mercurio.client.feature.entry.application.NoOpErrorReporter
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.application.UNKNOWN_CLIENT_VERSION
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.data.DefaultGatewayClient
import org.mercurio.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.mercurio.client.feature.entry.data.EndpointScopedRunRecoveryRegistry
import org.mercurio.client.feature.entry.data.HttpMessageImagePort
import org.mercurio.client.feature.entry.data.RollingLocalDiagnosticsBuffer
import org.mercurio.client.feature.entry.data.defaultMessageImageHttpTransport
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.presentation.EntryStateHolder
import org.mercurio.client.feature.entry.presentation.EntryStateHolderDependencies
import org.mercurio.client.feature.entry.presentation.LocalDiagnosticsPorts
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object EntryWiring {
    fun createMessageImagePort(): MessageImagePort = HttpMessageImagePort(defaultMessageImageHttpTransport())

    fun createEntryStateHolder(
        context: Context,
        overrides: EntryWiringOverrides = EntryWiringOverrides(),
        requestRunStatusNotificationPermission: (() -> Unit)? = null,
        requestAppUnlock: (() -> Unit)? = null,
    ): EntryStateHolder {
        val dataSource = SharedPreferencesGatewayConnectionDataSource(context)
        val credentialStore = overrides.credentialStore ?: AndroidKeyStoreGatewayCredentialStore(context)
        val repository = DefaultGatewayConnectionRepository(dataSource, credentialStore)
        val recoveryEndpoint = AtomicReference(dataSource.loadEndpoint())
        val runRecoveryRegistry = buildRunRecoveryRegistry(context, recoveryEndpoint)
        val initialState = LoadEntryState(repository, resolveInstalledBuildProvider(context, overrides)).execute()
        val clientVersion = initialState.clientVersion
        val verifyGatewayConnection = buildVerifyGatewayConnection(repository, overrides)
        val runSubmissionUncertaintyStore = SharedPreferencesRunSubmissionUncertaintyStore(context)
        val sessionDraftStore = SharedPreferencesSessionDraftStore(context)
        val modelSelectionStore = SharedPreferencesModelSelectionStore(context)
        val historyCacheStore = overrides.historyCacheStore ?: buildHistoryCacheStore(context)
        val localDiagnosticsBuffer = buildLocalDiagnosticsBuffer(context)
        val errorReportingConsentStore =
            overrides.errorReportingConsentStore ?: SharedPreferencesErrorReportingConsentStore(context)
        return EntryStateHolder(
            initialState = initialState,
            verifyGatewayConnection = verifyGatewayConnection,
            scope = entryScope(overrides.coroutineScope),
            sessionGatewayFactory = overrides.sessionGatewayFactory ?: ::newGatewayClient,
            runGatewayFactory = overrides.runGatewayFactory ?: ::newGatewayClient,
            dependencies =
                EntryStateHolderDependencies(
                    runRecoveryRegistry = runRecoveryRegistry,
                    updateRunRecoveryEndpoint = recoveryEndpoint::set,
                    persistRunRecoveryEntry = { endpoint, entry ->
                        runRecoveryRegistry.saveForEndpoint(endpoint, entry)
                    },
                    removeRunRecoveryEntry = { endpoint, entry ->
                        runRecoveryRegistry.removeForEndpoint(endpoint, entry)
                    },
                    removeGatewayConnectionUseCase =
                        buildConnectionRemovalUseCase(
                            repository,
                            runRecoveryRegistry,
                            localDiagnosticsBuffer,
                        ) { endpoint ->
                            endpoint?.let(runSubmissionUncertaintyStore::clearEndpoint)
                            endpoint?.let(sessionDraftStore::clearEndpoint)
                            endpoint?.let(modelSelectionStore::clearEndpoint)
                            endpoint?.let(historyCacheStore::clearEndpoint)
                        },
                    localDiagnostics = buildDiagnosticsPorts(context, localDiagnosticsBuffer, clientVersion),
                    errorReporting = buildErrorReporting(context, overrides, clientVersion, errorReportingConsentStore),
                    errorReportingConsentStore = errorReportingConsentStore,
                    isErrorReportingConfigured = resolvedSentryDsn(overrides.sentryDsn).isNotBlank(),
                    runSubmissionUncertaintyStore = runSubmissionUncertaintyStore,
                    sessionDraftStore = sessionDraftStore,
                    modelSelectionStore = modelSelectionStore,
                    historyCacheStore = historyCacheStore,
                    runStatusNotificationSettingsStore = SharedPreferencesRunStatusNotificationSettingsStore(context),
                    imageLoadingSettingsStore = SharedPreferencesImageLoadingSettingsStore(context),
                    runStatusNotificationPermission = AndroidRunStatusNotificationPermission(context),
                    runStatusNotifier = SystemRunStatusNotifier(context),
                    requestRunStatusNotificationPermission = requestRunStatusNotificationPermission,
                    appLock = appLockPorts(context, overrides, requestAppUnlock),
                ),
        )
    }

    /** Binds the error-reporting port for this installation, without building the state holder. */
    fun createErrorReporting(
        context: Context,
        overrides: EntryWiringOverrides = EntryWiringOverrides(),
    ): ErrorReporting =
        buildErrorReporting(
            context = context,
            overrides = overrides,
            clientVersion =
                resolveInstalledBuildProvider(context, overrides)
                    .installedBuild()
                    .versionNameOr(UNKNOWN_CLIENT_VERSION),
            consentStore =
                overrides.errorReportingConsentStore ?: SharedPreferencesErrorReportingConsentStore(context),
        )

    private fun entryScope(
        coroutineScope: CoroutineScope?,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): CoroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)
}

private fun resolveInstalledBuildProvider(
    context: Context,
    overrides: EntryWiringOverrides,
): InstalledBuildProvider = overrides.installedBuildProvider ?: AndroidInstalledBuildProvider(context)

private fun buildLocalDiagnosticsBuffer(context: Context): RollingLocalDiagnosticsBuffer =
    RollingLocalDiagnosticsBuffer(
        FileLocalDiagnosticsStorage(context),
        onRecordFailure = ::reportLocalDiagnosticsRecordFailure,
    )

private fun buildRunRecoveryRegistry(
    context: Context,
    recoveryEndpoint: AtomicReference<String?>,
): EndpointScopedRunRecoveryRegistry =
    EndpointScopedRunRecoveryRegistry(
        endpointProvider = recoveryEndpoint::get,
        storageForEndpoint = { endpoint ->
            SharedPreferencesRunRecoveryStorage(context) { endpoint }
        },
    )

private fun buildErrorReporting(
    context: Context,
    overrides: EntryWiringOverrides,
    clientVersion: String,
    consentStore: ErrorReportingConsentStore,
): ErrorReporting {
    val config =
        sentryReportingConfig(
            clientVersion = clientVersion,
            consent = consentStore.read(),
            dsn = overrides.sentryDsn,
        )
    val backend = overrides.sentryBackend ?: defaultSentryBackend
    return ErrorReporting(
        reporter = overrides.errorReporter ?: reporterFor(context, config, backend, consentStore),
        release = config.release,
        environment = config.environment,
    )
}

private fun reporterFor(
    context: Context,
    config: SentryReportingConfig,
    backend: SentryBackend,
    consentStore: ErrorReportingConsentStore,
): ErrorReporter = if (config.isEnabled) backend.start(context, config, consentStore::read) else NoOpErrorReporter

private fun buildVerifyGatewayConnection(
    repository: GatewayConnectionRepository,
    overrides: EntryWiringOverrides,
): VerifyGatewayConnection =
    VerifyGatewayConnection(repository) { endpoint, bearerCredential ->
        overrides.capabilityDiscovery?.invoke(endpoint, bearerCredential)
            ?: DefaultGatewayClient(endpoint, bearerCredential).discoverCapabilities()
    }

private fun buildConnectionRemovalUseCase(
    repository: GatewayConnectionRepository,
    runRecoveryRegistry: EndpointScopedRunRecoveryRegistry,
    localDiagnosticsBuffer: RollingLocalDiagnosticsBuffer,
    onEndpointRemoved: (String?) -> Unit,
): RemoveGatewayConnection =
    RemoveGatewayConnection(repository) { endpoint ->
        runRecoveryRegistry.clearForEndpoint(endpoint)
        onEndpointRemoved(endpoint)
        localDiagnosticsBuffer.clear()
    }

private fun buildDiagnosticsPorts(
    context: Context,
    buffer: RollingLocalDiagnosticsBuffer,
    clientVersion: String,
): LocalDiagnosticsPorts =
    LocalDiagnosticsPorts(
        recorder = buffer,
        store = buffer,
        exporter =
            AndroidLocalDiagnosticsExporter(
                context = context,
                buffer = buffer,
                clientVersion = clientVersion,
            ),
    )

private fun newGatewayClient(
    endpoint: String,
    bearerCredential: String,
): DefaultGatewayClient = DefaultGatewayClient(endpoint, bearerCredential)

private const val LOCAL_DIAGNOSTICS_LOG_TAG = "LocalDiagnostics"

private val localDiagnosticsRecordFailureReported = AtomicBoolean(false)

private fun reportLocalDiagnosticsRecordFailure(error: Throwable) {
    if (localDiagnosticsRecordFailureReported.compareAndSet(false, true)) {
        Log.w(
            LOCAL_DIAGNOSTICS_LOG_TAG,
            "Local diagnostics recording failed; recording stays disabled until a later attempt succeeds.",
            error,
        )
    }
}

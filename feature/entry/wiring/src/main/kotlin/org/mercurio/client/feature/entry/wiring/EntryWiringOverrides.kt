package org.mercurio.client.feature.entry.wiring

import kotlinx.coroutines.CoroutineScope
import org.mercurio.client.feature.entry.application.ErrorReporter
import org.mercurio.client.feature.entry.domain.AppLockEnrolment
import org.mercurio.client.feature.entry.domain.AppLockSettingsStore
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayCredentialStore
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import org.mercurio.client.feature.entry.domain.NightlyUpdateFeed
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.SessionGatewayPort

data class EntryWiringOverrides(
    val capabilityDiscovery: ((String, String) -> GatewayCapabilities)? = null,
    val sessionGatewayFactory: ((String, String) -> SessionGatewayPort)? = null,
    val runGatewayFactory: ((String, String) -> RunGatewayPort)? = null,
    val coroutineScope: CoroutineScope? = null,
    val credentialStore: GatewayCredentialStore? = null,
    val historyCacheStore: HistoryCacheStore? = null,
    val installedBuildProvider: InstalledBuildProvider? = null,
    val nightlyUpdateFeed: NightlyUpdateFeed? = null,
    val errorReporter: ErrorReporter? = null,
    val sentryBackend: SentryBackend? = null,
    val errorReportingConsentStore: ErrorReportingConsentStore? = null,
    val appLockSettingsStore: AppLockSettingsStore? = null,
    val appLockEnrolment: AppLockEnrolment? = null,
    val sentryDsn: String? = null,
)

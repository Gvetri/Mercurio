package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.CheckForNightlyUpdate
import org.mercurio.client.feature.entry.application.DownloadNightlyUpdate
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.InstallNightlyUpdate
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import org.mercurio.client.feature.entry.domain.NightlyApk
import org.mercurio.client.feature.entry.domain.NightlyApkDownload
import org.mercurio.client.feature.entry.domain.NightlyApkEvidence
import org.mercurio.client.feature.entry.domain.NightlyApkEvidenceSource
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyFeedFailure
import org.mercurio.client.feature.entry.domain.NightlyInstallOutcome
import org.mercurio.client.feature.entry.domain.NightlyInstallRefusal
import org.mercurio.client.feature.entry.domain.NightlyInstaller
import org.mercurio.client.feature.entry.domain.NightlyListing
import org.mercurio.client.feature.entry.domain.NightlyRelease
import org.mercurio.client.feature.entry.domain.NightlySigningMetadata
import org.mercurio.client.feature.entry.domain.NightlySigningMetadataResult
import org.mercurio.client.feature.entry.domain.NightlyTransfer
import org.mercurio.client.feature.entry.domain.NightlyUpdateFeed
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import java.time.Instant

private const val UPDATE_SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567"

private const val UPDATE_APK_SHA = "1111111111111111111111111111111111111111111111111111111111111111"

private const val UPDATE_CERTIFICATE_SHA = "2222222222222222222222222222222222222222222222222222222222222222"

private const val UPDATE_APK_BYTES = 13_002_342L

private const val UPDATE_VERSION_NAME = "nightly-7002-0123456789ab"

private const val UPDATE_VERSION_CODE = 7002L

private val UPDATE_PUBLISHED_AT = Instant.parse("2026-11-05T18:31:00Z")

private val UPDATE_CHECKED_AT = Instant.parse("2026-11-05T19:41:00Z")

private val UPDATE_RELEASE =
    NightlyRelease(
        tag = "nightly-7002-$UPDATE_SOURCE_SHA",
        publishedAt = UPDATE_PUBLISHED_AT,
        metadataAddress =
            "https://github.com/Gvetri/Mercurio/releases/download/" +
                "nightly-7002-$UPDATE_SOURCE_SHA/signing-metadata.json",
        apkAddress = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
        apkSizeBytes = UPDATE_APK_BYTES,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class EntryStateHolderAppUpdatesTest {
    @Test
    fun a_new_holder_holds_no_result_and_sends_no_request() =
        runTest {
            val feed = FakeUpdateFeed()

            val holder = updatesHolder(feed)

            try {
                assertEquals(AppUpdateStatus.Idle, holder.uiState.value.appUpdates.status)
                assertEquals(0, feed.listingCalls)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_tap_runs_one_check_and_reports_the_offered_build() =
        runTest {
            val feed =
                FakeUpdateFeed(
                    listing = NightlyListing.Listed(listOf(UPDATE_RELEASE)),
                    metadata = NightlySigningMetadataResult.Available(offeredMetadata()),
                )
            val holder = updatesHolder(feed)

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)

                assertEquals(AppUpdateStatus.Offered(offeredOffer()), holder.uiState.value.appUpdates.status)
                assertEquals(1, feed.listingCalls)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_tap_reports_up_to_date_when_no_newer_build_is_published() =
        runTest {
            val feed = FakeUpdateFeed(listing = NightlyListing.Listed(emptyList()))
            val holder = updatesHolder(feed)

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)

                assertEquals(AppUpdateStatus.UpToDate(UPDATE_CHECKED_AT), holder.uiState.value.appUpdates.status)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_tap_reports_a_contradictory_release_by_its_tag() =
        runTest {
            val contradictory = offeredMetadata(versionName = "nightly-7003-0123456789ab")
            val feed =
                FakeUpdateFeed(
                    listing = NightlyListing.Listed(listOf(UPDATE_RELEASE)),
                    metadata = NightlySigningMetadataResult.Available(contradictory),
                )
            val holder = updatesHolder(feed)

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)

                assertEquals(
                    AppUpdateStatus.Refused(UPDATE_RELEASE.tag, NightlyUpdateRefusal.CONTRADICTORY_RELEASE),
                    holder.uiState.value.appUpdates.status,
                )
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_tap_reports_an_offline_failure() =
        runTest {
            val feed = FakeUpdateFeed(listing = NightlyListing.Failed(NightlyFeedFailure.OFFLINE))
            val holder = updatesHolder(feed)

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)

                assertEquals(
                    AppUpdateStatus.Failed(NightlyUpdateFailure.OFFLINE),
                    holder.uiState.value.appUpdates.status,
                )
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_tap_is_ignored_when_google_play_manages_the_install() =
        runTest {
            val feed = FakeUpdateFeed(listing = NightlyListing.Listed(listOf(UPDATE_RELEASE)))
            val holder =
                EntryStateHolder(
                    initialState =
                        EntryState(
                            buildNumber = 6001L,
                            installSource = InstallSource.GOOGLE_PLAY,
                        ),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                    dependencies = EntryStateHolderDependencies(checkForNightlyUpdate = check(feed)),
                )

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)

                assertEquals(AppUpdateStatus.Idle, holder.uiState.value.appUpdates.status)
                assertEquals(0, feed.listingCalls)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_second_tap_cancels_the_check_in_flight_and_keeps_the_last_result() =
        runTest {
            val feed = GatedUpdateFeed()
            val holder = updatesHolder(feed, StandardTestDispatcher(testScheduler))

            try {
                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)
                runCurrent()
                assertTrue(feed.firstCallStarted)

                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)
                advanceUntilIdle()

                assertTrue(feed.firstCallCancelled)
                assertEquals(AppUpdateStatus.UpToDate(UPDATE_CHECKED_AT), holder.uiState.value.appUpdates.status)
                assertEquals(2, feed.listingCalls)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_download_tap_reports_the_progress_of_the_running_transfer() =
        runTest {
            val download = GatedApkDownload()
            val holder = offeredHolder(download, StandardTestDispatcher(testScheduler)) { runCurrent() }

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                runCurrent()

                assertTrue(download.started)
                assertEquals(
                    AppUpdateStatus.Downloading(NightlyDownloadProgress(UPDATE_APK_BYTES / 2, UPDATE_APK_BYTES)),
                    holder.uiState.value.appUpdates.status,
                )
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_verified_download_without_an_install_stage_keeps_the_artifact_and_reports_verified() =
        runTest {
            val download = CompletingApkDownload()
            val holder = offeredHolder(download, Dispatchers.Unconfined) { }

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(AppUpdateStatus.Verified(UPDATE_CHECKED_AT), holder.uiState.value.appUpdates.status)
                assertTrue(download.discarded.isEmpty())
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_verified_download_hands_the_artifact_to_the_install_stage_and_reports_installed() =
        runTest {
            val download = CompletingApkDownload()
            val installer = FakeNightlyInstaller(outcome = NightlyInstallOutcome.Installed)
            val holder = offeredHolder(download, Dispatchers.Unconfined, installer)

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.Installed(UPDATE_VERSION_NAME, UPDATE_VERSION_CODE),
                    holder.uiState.value.appUpdates.status,
                )
                assertEquals(listOf(ARTIFACT), installer.installed)
                assertEquals(listOf(ARTIFACT), download.discarded)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_missing_install_permission_reports_the_gate_before_anything_is_staged() =
        runTest {
            val download = CompletingApkDownload()
            val installer = FakeNightlyInstaller(allowed = false)
            val holder = offeredHolder(download, Dispatchers.Unconfined, installer)

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.PermissionRequired(UPDATE_VERSION_NAME, UPDATE_VERSION_CODE),
                    holder.uiState.value.appUpdates.status,
                )
                assertTrue(installer.installed.isEmpty())
                assertTrue(download.discarded.isEmpty())
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_granted_permission_resumes_the_install_when_the_app_returns_to_the_foreground() =
        runTest {
            val download = CompletingApkDownload()
            val installer = FakeNightlyInstaller(allowed = false)
            val holder = offeredHolder(download, Dispatchers.Unconfined, installer)

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()
                assertEquals(
                    AppUpdateStatus.PermissionRequired(UPDATE_VERSION_NAME, UPDATE_VERSION_CODE),
                    holder.uiState.value.appUpdates.status,
                )

                installer.allowed = true
                holder.onEvent(EntryUiEvent.AppEnteredBackground)
                holder.onEvent(EntryUiEvent.AppEnteredForeground)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.Installed(UPDATE_VERSION_NAME, UPDATE_VERSION_CODE),
                    holder.uiState.value.appUpdates.status,
                )
                assertEquals(listOf(ARTIFACT), installer.installed)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_still_missing_permission_keeps_the_gate_when_the_app_returns_to_the_foreground() =
        runTest {
            val download = CompletingApkDownload()
            val installer = FakeNightlyInstaller(allowed = false)
            val holder = offeredHolder(download, Dispatchers.Unconfined, installer)

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                holder.onEvent(EntryUiEvent.AppEnteredBackground)
                holder.onEvent(EntryUiEvent.AppEnteredForeground)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.PermissionRequired(UPDATE_VERSION_NAME, UPDATE_VERSION_CODE),
                    holder.uiState.value.appUpdates.status,
                )
                assertTrue(installer.installed.isEmpty())
            } finally {
                holder.close()
            }
        }

    @Test
    fun an_install_refusal_is_reported_with_its_reason_and_the_artifact_is_removed() =
        runTest {
            val download = CompletingApkDownload()
            val refusal = NightlyInstallOutcome.Refused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM)
            val holder = offeredHolder(download, Dispatchers.Unconfined, FakeNightlyInstaller(outcome = refusal))

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.InstallRefused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM),
                    holder.uiState.value.appUpdates.status,
                )
                assertEquals(listOf(ARTIFACT), download.discarded)
            } finally {
                holder.close()
            }
        }

    @Test
    fun an_insufficient_storage_refusal_is_reported_and_the_artifact_is_removed() =
        runTest {
            val download = CompletingApkDownload()
            val refusal = NightlyInstallOutcome.Refused(NightlyInstallRefusal.INSUFFICIENT_STORAGE)
            val holder = offeredHolder(download, Dispatchers.Unconfined, FakeNightlyInstaller(outcome = refusal))

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(
                    AppUpdateStatus.InstallRefused(NightlyInstallRefusal.INSUFFICIENT_STORAGE),
                    holder.uiState.value.appUpdates.status,
                )
                assertEquals(listOf(ARTIFACT), download.discarded)
            } finally {
                holder.close()
            }
        }

    @Test
    fun opening_the_install_settings_is_reported_only_at_the_permission_gate() =
        runTest {
            val download = CompletingApkDownload()
            val installer = FakeNightlyInstaller(allowed = false)
            var settingsRequests = 0
            val holder =
                offeredHolder(
                    download,
                    Dispatchers.Unconfined,
                    installer,
                    handlers = LifecycleHandlers(settingsRequests = { settingsRequests++ }),
                )

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                holder.onEvent(EntryUiEvent.OpenInstallPermissionSettingsClicked)

                assertEquals(1, settingsRequests)
            } finally {
                holder.close()
            }
        }

    @Test
    fun restarting_to_finish_is_reported_only_after_the_install() =
        runTest {
            val download = CompletingApkDownload()
            var restarts = 0
            val holder =
                offeredHolder(
                    download,
                    Dispatchers.Unconfined,
                    FakeNightlyInstaller(outcome = NightlyInstallOutcome.Installed),
                    handlers = LifecycleHandlers(restarts = { restarts++ }),
                )

            try {
                holder.onEvent(EntryUiEvent.RestartToFinishClicked)
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                advanceUntilIdle()

                assertEquals(0, restarts)

                holder.onEvent(EntryUiEvent.RestartToFinishClicked)

                assertEquals(1, restarts)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_cancelled_download_returns_to_the_offer_and_stops_the_transfer() =
        runTest {
            val download = GatedApkDownload()
            val holder = offeredHolder(download, StandardTestDispatcher(testScheduler)) { runCurrent() }

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                runCurrent()
                assertTrue(download.started)

                holder.onEvent(EntryUiEvent.CancelUpdateDownloadClicked)
                advanceUntilIdle()

                assertTrue(download.cancelled)
                assertEquals(AppUpdateStatus.Offered(offeredOffer()), holder.uiState.value.appUpdates.status)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_new_check_cancels_a_download_in_flight() =
        runTest {
            val download = GatedApkDownload()
            val holder = offeredHolder(download, StandardTestDispatcher(testScheduler)) { runCurrent() }

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)
                runCurrent()
                assertTrue(download.started)

                holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)
                advanceUntilIdle()

                assertTrue(download.cancelled)
                assertEquals(AppUpdateStatus.Offered(offeredOffer()), holder.uiState.value.appUpdates.status)
            } finally {
                holder.close()
            }
        }

    @Test
    fun a_download_tap_without_an_offer_does_nothing() =
        runTest {
            val download = CompletingApkDownload()
            val holder = updatesHolder(FakeUpdateFeed(), download = download)

            try {
                holder.onEvent(EntryUiEvent.DownloadUpdateClicked)

                assertEquals(AppUpdateStatus.Idle, holder.uiState.value.appUpdates.status)
                assertTrue(download.discarded.isEmpty())
            } finally {
                holder.close()
            }
        }

    private fun offeredHolder(
        download: NightlyApkDownload,
        dispatcher: CoroutineDispatcher,
        installer: NightlyInstaller? = null,
        handlers: LifecycleHandlers = LifecycleHandlers(),
        advance: () -> Unit = {},
    ): EntryStateHolder {
        val feed =
            FakeUpdateFeed(
                listing = NightlyListing.Listed(listOf(UPDATE_RELEASE)),
                metadata = NightlySigningMetadataResult.Available(offeredMetadata()),
            )
        return updatesHolder(feed, dispatcher, download, installer, handlers).also { holder ->
            holder.onEvent(EntryUiEvent.CheckForUpdatesClicked)
            advance()
        }
    }

    private fun updatesHolder(
        feed: NightlyUpdateFeed,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        download: NightlyApkDownload = CompletingApkDownload(),
        installer: NightlyInstaller? = null,
        handlers: LifecycleHandlers = LifecycleHandlers(),
    ): EntryStateHolder =
        holder(
            repository = FakeGatewayConnectionRepository(),
            verifier = { _, _ -> clientManifestCapabilities() },
            dispatcher = dispatcher,
            dependencies =
                EntryStateHolderDependencies(
                    checkForNightlyUpdate = check(feed),
                    downloadNightlyUpdate =
                        DownloadNightlyUpdate(
                            download = download,
                            evidence = FakeApkEvidenceSource(),
                            now = { UPDATE_CHECKED_AT },
                        ),
                    installNightlyUpdate = installer?.let { chosenInstaller -> InstallNightlyUpdate(chosenInstaller) },
                    requestInstallPermissionSettings = handlers.settingsRequests,
                    requestAppRestart = handlers.restarts,
                ),
        )

    private fun check(feed: NightlyUpdateFeed): CheckForNightlyUpdate =
        CheckForNightlyUpdate(
            feed = feed,
            installedBuildProvider =
                FixedInstalledBuildProvider(
                    InstalledBuild(
                        versionCode = 6001L,
                        versionName = "nightly-6001-89abcdef0123",
                        installSource = InstallSource.OTHER_SOURCE,
                    ),
                ),
            now = { UPDATE_CHECKED_AT },
        )

    private fun offeredMetadata(versionName: String = UPDATE_VERSION_NAME): NightlySigningMetadata =
        NightlySigningMetadata(
            sourceSha = UPDATE_SOURCE_SHA,
            versionCode = 7002L,
            versionName = versionName,
            nativeAbis = listOf("arm64-v8a"),
            apkSha256 = UPDATE_APK_SHA,
            certificateSha256 = UPDATE_CERTIFICATE_SHA,
        )

    private fun offeredOffer(): NightlyUpdateOffer =
        NightlyUpdateOffer(
            releaseTag = UPDATE_RELEASE.tag,
            versionName = UPDATE_VERSION_NAME,
            versionCode = 7002L,
            sourceCommit = UPDATE_SOURCE_SHA.take(7),
            publishedAt = UPDATE_PUBLISHED_AT,
            apk =
                NightlyApk(
                    address = UPDATE_RELEASE.apkAddress,
                    sizeBytes = UPDATE_APK_BYTES,
                    sha256 = UPDATE_APK_SHA,
                    certificateSha256 = UPDATE_CERTIFICATE_SHA,
                    nativeAbis = listOf("arm64-v8a"),
                ),
        )
}

private val ARTIFACT =
    NightlyArtifact(reference = "nightly-update.apk", sizeBytes = UPDATE_APK_BYTES, sha256 = UPDATE_APK_SHA)

private class FakeUpdateFeed(
    private val listing: NightlyListing = NightlyListing.Listed(emptyList()),
    private val metadata: NightlySigningMetadataResult =
        NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE),
) : NightlyUpdateFeed {
    var listingCalls: Int = 0
        private set

    override suspend fun publishedNightlies(): NightlyListing {
        listingCalls++
        return listing
    }

    override suspend fun signingMetadata(release: NightlyRelease): NightlySigningMetadataResult = metadata
}

private class GatedUpdateFeed : NightlyUpdateFeed {
    var listingCalls: Int = 0
        private set
    var firstCallStarted: Boolean = false
        private set
    var firstCallCancelled: Boolean = false
        private set

    override suspend fun publishedNightlies(): NightlyListing {
        listingCalls++
        return if (listingCalls == 1) {
            firstCallStarted = true
            try {
                awaitCancellation()
            } finally {
                firstCallCancelled = true
            }
        } else {
            NightlyListing.Listed(emptyList())
        }
    }

    override suspend fun signingMetadata(release: NightlyRelease): NightlySigningMetadataResult =
        NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
}

private class CompletingApkDownload : NightlyApkDownload {
    val discarded: MutableList<NightlyArtifact> = mutableListOf()

    override suspend fun transfer(
        apk: NightlyApk,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ): NightlyTransfer {
        onProgress(NightlyDownloadProgress(apk.sizeBytes, apk.sizeBytes))
        return NightlyTransfer.Transferred(ARTIFACT)
    }

    override fun discard(artifact: NightlyArtifact) {
        discarded += artifact
    }
}

private class GatedApkDownload : NightlyApkDownload {
    var started: Boolean = false
        private set
    var cancelled: Boolean = false
        private set

    override suspend fun transfer(
        apk: NightlyApk,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ): NightlyTransfer {
        started = true
        onProgress(NightlyDownloadProgress(apk.sizeBytes / 2, apk.sizeBytes))
        try {
            awaitCancellation()
        } finally {
            cancelled = true
        }
    }

    override fun discard(artifact: NightlyArtifact) = Unit
}

private class FakeApkEvidenceSource : NightlyApkEvidenceSource {
    override fun apkEvidence(artifact: NightlyArtifact): NightlyApkEvidence? =
        NightlyApkEvidence(
            versionCode = 7002L,
            versionName = UPDATE_VERSION_NAME,
            certificateSha256 = UPDATE_CERTIFICATE_SHA,
            nativeAbis = listOf("arm64-v8a"),
        )

    override fun installedSignerSha256(): String? = UPDATE_CERTIFICATE_SHA

    override fun supportedAbis(): List<String> = listOf("arm64-v8a")
}

private class FixedInstalledBuildProvider(
    private val build: InstalledBuild,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild = build
}

private class FakeNightlyInstaller(
    var allowed: Boolean = true,
    private val outcome: NightlyInstallOutcome = NightlyInstallOutcome.Installed,
) : NightlyInstaller {
    val installed: MutableList<NightlyArtifact> = mutableListOf()

    override fun installAllowed(): Boolean = allowed

    override suspend fun install(artifact: NightlyArtifact): NightlyInstallOutcome {
        installed += artifact
        return outcome
    }
}

private class LifecycleHandlers(
    val settingsRequests: (() -> Unit)? = null,
    val restarts: (() -> Unit)? = null,
)

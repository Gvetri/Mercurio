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
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.NightlyUpdateFailure
import org.mercurio.client.feature.entry.application.NightlyUpdateRefusal
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import org.mercurio.client.feature.entry.domain.NightlyFeedFailure
import org.mercurio.client.feature.entry.domain.NightlyListing
import org.mercurio.client.feature.entry.domain.NightlyRelease
import org.mercurio.client.feature.entry.domain.NightlySigningMetadata
import org.mercurio.client.feature.entry.domain.NightlySigningMetadataResult
import org.mercurio.client.feature.entry.domain.NightlyUpdateFeed
import java.time.Instant

private const val UPDATE_SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567"

private val UPDATE_PUBLISHED_AT = Instant.parse("2026-11-05T18:31:00Z")

private val UPDATE_CHECKED_AT = Instant.parse("2026-11-05T19:41:00Z")

private val UPDATE_RELEASE =
    NightlyRelease(
        tag = "nightly-7002-$UPDATE_SOURCE_SHA",
        publishedAt = UPDATE_PUBLISHED_AT,
        metadataAddress =
            "https://github.com/Gvetri/Mercurio/releases/download/" +
                "nightly-7002-$UPDATE_SOURCE_SHA/signing-metadata.json",
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

                assertEquals(
                    AppUpdateStatus.Offered(
                        versionName = "nightly-7002-0123456789ab",
                        sourceCommit = "0123456",
                        publishedAt = UPDATE_PUBLISHED_AT,
                        releaseTag = UPDATE_RELEASE.tag,
                    ),
                    holder.uiState.value.appUpdates.status,
                )
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

    private fun updatesHolder(
        feed: NightlyUpdateFeed,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): EntryStateHolder =
        holder(
            repository = FakeGatewayConnectionRepository(),
            verifier = { _, _ -> clientManifestCapabilities() },
            dispatcher = dispatcher,
            dependencies = EntryStateHolderDependencies(checkForNightlyUpdate = check(feed)),
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

    private fun offeredMetadata(versionName: String = "nightly-7002-0123456789ab"): NightlySigningMetadata =
        NightlySigningMetadata(
            sourceSha = UPDATE_SOURCE_SHA,
            versionCode = 7002L,
            versionName = versionName,
            nativeAbis = listOf("arm64-v8a"),
            apkSha256 = "1111111111111111111111111111111111111111111111111111111111111111",
            certificateSha256 = "2222222222222222222222222222222222222222222222222222222222222222",
        )
}

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

private class FixedInstalledBuildProvider(
    private val build: InstalledBuild,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild = build
}

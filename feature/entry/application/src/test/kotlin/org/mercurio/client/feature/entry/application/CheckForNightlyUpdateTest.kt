package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
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

private const val NEW_SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567"

private const val OLD_SOURCE_SHA = "89abcdef0123456789abcdef0123456789abcdef"

private const val APK_SHA = "1111111111111111111111111111111111111111111111111111111111111111"

private const val CERTIFICATE_SHA = "2222222222222222222222222222222222222222222222222222222222222222"

private val CHECKED_AT = Instant.parse("2026-11-05T19:41:00Z")

private val NEW_RELEASE =
    NightlyRelease(
        tag = "nightly-7002-$NEW_SOURCE_SHA",
        publishedAt = Instant.parse("2026-11-05T18:31:00Z"),
        metadataAddress =
            "https://github.com/Gvetri/Mercurio/releases/download/" +
                "nightly-7002-$NEW_SOURCE_SHA/signing-metadata.json",
        apkAddress = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
        apkSizeBytes = 13_002_342L,
    )

private val OLD_RELEASE =
    NightlyRelease(
        tag = "nightly-6001-$OLD_SOURCE_SHA",
        publishedAt = Instant.parse("2026-11-04T18:31:00Z"),
        metadataAddress =
            "https://github.com/Gvetri/Mercurio/releases/download/" +
                "nightly-6001-$OLD_SOURCE_SHA/signing-metadata.json",
        apkAddress = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/9",
        apkSizeBytes = 13_002_100L,
    )

private fun metadata(
    sourceSha: String = NEW_SOURCE_SHA,
    versionCode: Long = 7002L,
    versionName: String = "nightly-7002-0123456789ab",
): NightlySigningMetadata =
    NightlySigningMetadata(
        sourceSha = sourceSha,
        versionCode = versionCode,
        versionName = versionName,
        nativeAbis = listOf("arm64-v8a"),
        apkSha256 = APK_SHA,
        certificateSha256 = CERTIFICATE_SHA,
    )

class CheckForNightlyUpdateTest {
    @Test
    fun offers_the_newest_nightly_with_its_identifiers() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(OLD_RELEASE, NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to NightlySigningMetadataResult.Available(metadata())),
                )

            val result = checker(feed, installedVersionCode = 6001L).execute() as NightlyUpdateCheck.Offered

            assertEquals("nightly-7002-0123456789ab", result.offer.versionName)
            assertEquals("0123456", result.offer.sourceCommit)
            assertEquals(NEW_RELEASE.publishedAt, result.offer.publishedAt)
            assertEquals(NEW_RELEASE.tag, result.offer.releaseTag)
            assertEquals(CHECKED_AT, result.checkedAt)
            assertEquals(listOf(NEW_RELEASE), feed.metadataRequests)
        }

    @Test
    fun the_offer_carries_the_apk_address_size_and_verification_values() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to NightlySigningMetadataResult.Available(metadata())),
                )

            val result = checker(feed, installedVersionCode = 6001L).execute() as NightlyUpdateCheck.Offered

            assertEquals(NEW_RELEASE.apkAddress, result.offer.apk.address)
            assertEquals(NEW_RELEASE.apkSizeBytes, result.offer.apk.sizeBytes)
            assertEquals(APK_SHA, result.offer.apk.sha256)
            assertEquals(CERTIFICATE_SHA, result.offer.apk.certificateSha256)
            assertEquals(listOf("arm64-v8a"), result.offer.apk.nativeAbis)
            assertEquals(7002L, result.offer.versionCode)
        }

    @Test
    fun reports_up_to_date_when_the_offered_build_is_the_installed_build() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to NightlySigningMetadataResult.Available(metadata())),
                )

            assertEquals(
                NightlyUpdateCheck.UpToDate(CHECKED_AT),
                checker(feed, installedVersionCode = 7002L).execute(),
            )
        }

    @Test
    fun reports_up_to_date_when_the_offered_build_is_older_than_the_installed_build() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to NightlySigningMetadataResult.Available(metadata())),
                )

            assertEquals(
                NightlyUpdateCheck.UpToDate(CHECKED_AT),
                checker(feed, installedVersionCode = 7003L).execute(),
            )
        }

    @Test
    fun reports_up_to_date_when_the_listing_has_no_published_nightly() =
        runTest {
            val feed = FakeNightlyUpdateFeed(listing = NightlyListing.Listed(emptyList()))

            assertEquals(
                NightlyUpdateCheck.UpToDate(CHECKED_AT),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
            assertEquals(emptyList<NightlyRelease>(), feed.metadataRequests)
        }

    @Test
    fun refuses_a_contradictory_document_by_its_release_tag_without_falling_back() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE, OLD_RELEASE)),
                    metadata =
                        mapOf(
                            NEW_RELEASE.tag to
                                NightlySigningMetadataResult.Available(
                                    metadata(versionName = "nightly-7003-0123456789ab"),
                                ),
                            OLD_RELEASE.tag to NightlySigningMetadataResult.Available(metadata()),
                        ),
                )

            val result = checker(feed, installedVersionCode = 6001L).execute() as NightlyUpdateCheck.Refused

            assertEquals(NEW_RELEASE.tag, result.releaseTag)
            assertEquals(NightlyUpdateRefusal.CONTRADICTORY_RELEASE, result.reason)
            assertEquals(listOf(NEW_RELEASE), feed.metadataRequests)
        }

    @Test
    fun reports_an_unreadable_document_as_a_service_failure_not_a_refusal() =
        runTest {
            val unreadable = NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to unreadable),
                )

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun refuses_to_offer_when_the_installed_build_cannot_be_read() =
        runTest {
            val feed = FakeNightlyUpdateFeed()

            val result = checker(feed, installedVersionCode = 0L).execute() as NightlyUpdateCheck.Refused

            assertEquals(null, result.releaseTag)
            assertEquals(NightlyUpdateRefusal.UNREADABLE_INSTALLED_BUILD, result.reason)
            assertEquals(0, feed.listingRequests)
        }

    @Test
    fun reports_an_offline_listing_as_its_own_failure() =
        runTest {
            val feed = FakeNightlyUpdateFeed(listing = NightlyListing.Failed(NightlyFeedFailure.OFFLINE))

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.OFFLINE),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun reports_a_rate_limit_as_its_own_failure() =
        runTest {
            val feed = FakeNightlyUpdateFeed(listing = NightlyListing.Failed(NightlyFeedFailure.RATE_LIMITED))

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.RATE_LIMITED),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun reports_a_malformed_listing_as_a_service_failure() =
        runTest {
            val feed = FakeNightlyUpdateFeed(listing = NightlyListing.Failed(NightlyFeedFailure.MALFORMED_RESPONSE))

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun reports_a_metadata_request_failure_after_a_successful_listing() =
        runTest {
            val rateLimited = NightlySigningMetadataResult.Failed(NightlyFeedFailure.RATE_LIMITED)
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to rateLimited),
                )

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.RATE_LIMITED),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun reports_an_offline_metadata_request() =
        runTest {
            val offline = NightlySigningMetadataResult.Failed(NightlyFeedFailure.OFFLINE)
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to offline),
                )

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.OFFLINE),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun reports_an_unavailable_metadata_request() =
        runTest {
            val unavailable = NightlySigningMetadataResult.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE)
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to unavailable),
                )

            assertEquals(
                NightlyUpdateCheck.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE),
                checker(feed, installedVersionCode = 6001L).execute(),
            )
        }

    @Test
    fun offers_a_build_to_a_client_whose_version_code_is_one() =
        runTest {
            val feed =
                FakeNightlyUpdateFeed(
                    listing = NightlyListing.Listed(listOf(NEW_RELEASE)),
                    metadata = mapOf(NEW_RELEASE.tag to NightlySigningMetadataResult.Available(metadata())),
                )

            val result = checker(feed, installedVersionCode = 1L).execute()

            assertEquals(NightlyUpdateCheck.Offered::class, result::class)
        }

    private fun checker(
        feed: FakeNightlyUpdateFeed,
        installedVersionCode: Long,
    ): CheckForNightlyUpdate =
        CheckForNightlyUpdate(
            feed = feed,
            installedBuildProvider =
                FakeInstalledBuildProvider(
                    InstalledBuild(
                        versionCode = installedVersionCode,
                        versionName = "nightly-$installedVersionCode-0123456789ab",
                        installSource = InstallSource.OTHER_SOURCE,
                    ),
                ),
            now = { CHECKED_AT },
        )
}

private class FakeNightlyUpdateFeed(
    private val listing: NightlyListing = NightlyListing.Listed(emptyList()),
    private val metadata: Map<String, NightlySigningMetadataResult> = emptyMap(),
) : NightlyUpdateFeed {
    var listingRequests: Int = 0
        private set
    val metadataRequests: MutableList<NightlyRelease> = mutableListOf()

    override suspend fun publishedNightlies(): NightlyListing {
        listingRequests++
        return listing
    }

    override suspend fun signingMetadata(release: NightlyRelease): NightlySigningMetadataResult {
        metadataRequests += release
        return metadata[release.tag] ?: NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
    }
}

private class FakeInstalledBuildProvider(
    private val build: InstalledBuild,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild = build
}

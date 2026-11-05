package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.NightlyFeedFailure
import org.mercurio.client.feature.entry.domain.NightlyListing
import org.mercurio.client.feature.entry.domain.NightlyRelease
import org.mercurio.client.feature.entry.domain.NightlySigningMetadata
import org.mercurio.client.feature.entry.domain.NightlySigningMetadataResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

private const val RELEASE_TAG = "nightly-7002-0123456789abcdef0123456789abcdef01234567"

private val RELEASE =
    NightlyRelease(
        tag = RELEASE_TAG,
        publishedAt = Instant.parse("2026-11-05T18:31:00Z"),
        metadataAddress = "https://github.com/Gvetri/Mercurio/releases/download/$RELEASE_TAG/signing-metadata.json",
    )

class GitHubNightlyUpdateFeedTest {
    @Test
    fun keeps_only_published_nightlies_with_every_channel_asset_newest_first() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("listing.json")) }

            val listing = feed(transport).publishedNightlies()

            val tags = (listing as NightlyListing.Listed).releases.map(NightlyRelease::tag)
            assertEquals(
                listOf(
                    RELEASE_TAG,
                    "nightly-6001-89abcdef0123456789abcdef0123456789abcdef",
                ),
                tags,
            )
            assertEquals(
                listOf("https://api.github.com/repos/Gvetri/Mercurio/releases?per_page=30"),
                transport.requestedUrls,
            )
        }

    @Test
    fun reads_the_signing_metadata_of_one_release() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("metadata.json")) }

            val result = feed(transport).signingMetadata(RELEASE)

            assertEquals(
                NightlySigningMetadataResult.Available(
                    NightlySigningMetadata(
                        sourceSha = "0123456789abcdef0123456789abcdef01234567",
                        versionCode = 7002L,
                        versionName = "nightly-7002-0123456789ab",
                        nativeAbis = listOf("arm64-v8a"),
                        apkSha256 = "1111111111111111111111111111111111111111111111111111111111111111",
                        certificateSha256 = "2222222222222222222222222222222222222222222222222222222222222222",
                    ),
                ),
                result,
            )
            assertEquals(listOf(RELEASE.metadataAddress), transport.requestedUrls)
        }

    @Test
    fun reports_a_document_whose_values_contradict_its_release_as_readable_values() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("metadata-contradictory.json")) }

            val metadata = (feed(transport).signingMetadata(RELEASE) as NightlySigningMetadataResult.Available).metadata

            assertEquals("nightly-7003-0123456789ab", metadata.versionName)
            assertEquals(7002L, metadata.versionCode)
        }

    @Test
    fun reports_a_rate_limited_listing() =
        runTest {
            val listing = feed(FakeGitHubTransport { error(403) }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.RATE_LIMITED), listing)
        }

    @Test
    fun reports_a_too_many_requests_listing() =
        runTest {
            val listing = feed(FakeGitHubTransport { error(429) }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.RATE_LIMITED), listing)
        }

    @Test
    fun reports_a_server_error_listing() =
        runTest {
            val listing = feed(FakeGitHubTransport { error(500) }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), listing)
        }

    @Test
    fun reports_an_unknown_host_as_offline() =
        runTest {
            val transport = FakeGitHubTransport { throw UnknownHostException("api.github.com") }
            val listing = feed(transport).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.OFFLINE), listing)
        }

    @Test
    fun reports_a_timeout_as_service_unavailable() =
        runTest {
            val listing = feed(FakeGitHubTransport { throw SocketTimeoutException("timed out") }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), listing)
        }

    @Test
    fun reports_a_malformed_listing() =
        runTest {
            val listing = feed(FakeGitHubTransport { ok("{ not json") }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.MALFORMED_RESPONSE), listing)
        }

    @Test
    fun reports_a_malformed_metadata_document() =
        runTest {
            val result = feed(FakeGitHubTransport { ok("{ not json") }).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE), result)
        }

    @Test
    fun reports_a_metadata_document_that_omits_a_required_field() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("metadata-missing-digest.json")) }
            val result = feed(transport).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE), result)
        }

    @Test
    fun refuses_a_metadata_address_that_is_not_https_without_a_request() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("metadata.json")) }
            val insecure = RELEASE.copy(metadataAddress = RELEASE.metadataAddress.replace("https://", "http://"))

            val result = feed(transport).signingMetadata(insecure)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE), result)
            assertTrue(transport.requestedUrls.isEmpty())
        }

    @Test
    fun reports_a_metadata_request_that_fails_at_the_transport() =
        runTest {
            val result = feed(FakeGitHubTransport { throw IOException("connection reset") }).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.OFFLINE), result)
        }

    private fun feed(transport: FakeGitHubTransport): GitHubNightlyUpdateFeed = GitHubNightlyUpdateFeed(transport)

    private fun payload(name: String): String =
        requireNotNull(javaClass.getResource("/nightly-update/$name")) { "missing payload $name" }.readText()

    private fun ok(body: String): GitHubReleaseHttpResponse = GitHubReleaseHttpResponse(statusCode = 200, body = body)

    private fun error(statusCode: Int): GitHubReleaseHttpResponse =
        GitHubReleaseHttpResponse(statusCode = statusCode, body = """{"message":"request refused"}""")
}

private class FakeGitHubTransport(
    private val respond: (String) -> GitHubReleaseHttpResponse,
) : GitHubReleaseHttpTransport {
    val requestedUrls: MutableList<String> = mutableListOf()

    override suspend fun get(request: GitHubReleaseHttpRequest): GitHubReleaseHttpResponse {
        requestedUrls += request.url
        return respond(request.url)
    }
}

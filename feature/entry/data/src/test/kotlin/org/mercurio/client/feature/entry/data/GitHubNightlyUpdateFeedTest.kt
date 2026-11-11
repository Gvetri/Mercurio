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

private const val ASSET_LOCATION =
    "https://release-assets.githubusercontent.com/github-production-release-asset/1/signing-metadata.json"

private val RELEASE =
    NightlyRelease(
        tag = RELEASE_TAG,
        publishedAt = Instant.parse("2026-11-05T18:31:00Z"),
        metadataAddress = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/3",
        apkAddress = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
        apkSizeBytes = 13_002_342L,
    )

class GitHubNightlyUpdateFeedTest {
    @Test
    fun keeps_only_published_nightlies_with_every_channel_asset_newest_first() =
        runTest {
            val transport = FakeGitHubTransport { ok(payload("listing.json")) }

            val listing = feed(transport).publishedNightlies()

            val releases = (listing as NightlyListing.Listed).releases
            val tags = releases.map(NightlyRelease::tag)
            assertEquals(
                listOf(
                    RELEASE_TAG,
                    "nightly-6001-89abcdef0123456789abcdef0123456789abcdef",
                ),
                tags,
            )
            assertEquals(
                "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/3",
                releases.first().metadataAddress,
            )
            assertEquals(
                "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
                releases.first().apkAddress,
            )
            assertEquals(13_002_342L, releases.first().apkSizeBytes)
            assertEquals(
                listOf("https://api.github.com/repos/Gvetri/Mercurio/releases?per_page=100"),
                transport.requestedUrls,
            )
            assertEquals(
                listOf(GitHubReleaseHttpRequest.GITHUB_API_MEDIA_TYPE),
                transport.requestedAccepts,
            )
        }

    @Test
    fun reads_the_signing_metadata_of_one_release() =
        runTest {
            val transport =
                FakeGitHubTransport { request ->
                    when (request.url) {
                        RELEASE.metadataAddress -> error(302, location = ASSET_LOCATION)
                        ASSET_LOCATION -> ok(payload("metadata.json"))
                        else -> error(500)
                    }
                }

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
            assertEquals(listOf(RELEASE.metadataAddress, ASSET_LOCATION), transport.requestedUrls)
            assertEquals(
                listOf(
                    GitHubReleaseHttpRequest.GITHUB_ASSET_MEDIA_TYPE,
                    GitHubReleaseHttpRequest.GITHUB_ASSET_MEDIA_TYPE,
                ),
                transport.requestedAccepts,
            )
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

    @Test
    fun reports_a_redirecting_metadata_response_as_service_unavailable() =
        runTest {
            val result = feed(FakeGitHubTransport { error(302) }).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), result)
        }

    @Test
    fun reports_a_redirect_to_a_foreign_host_as_service_unavailable() =
        runTest {
            val transport = FakeGitHubTransport { error(302, location = "https://example.invalid/metadata.json") }

            val result = feed(transport).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), result)
            assertEquals(listOf(RELEASE.metadataAddress), transport.requestedUrls)
        }

    @Test
    fun reports_a_second_redirect_as_service_unavailable() =
        runTest {
            val transport = FakeGitHubTransport { error(302, location = ASSET_LOCATION) }

            val result = feed(transport).signingMetadata(RELEASE)

            assertEquals(NightlySigningMetadataResult.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), result)
            assertEquals(listOf(RELEASE.metadataAddress, ASSET_LOCATION), transport.requestedUrls)
        }

    @Test
    fun reports_an_oversized_response_as_service_unavailable() =
        runTest {
            val listing = feed(FakeGitHubTransport { throw GitHubResponseTooLargeException() }).publishedNightlies()

            assertEquals(NightlyListing.Failed(NightlyFeedFailure.SERVICE_UNAVAILABLE), listing)
        }

    private fun feed(transport: FakeGitHubTransport): GitHubNightlyUpdateFeed = GitHubNightlyUpdateFeed(transport)

    private fun payload(name: String): String =
        requireNotNull(javaClass.getResource("/nightly-update/$name")) { "missing payload $name" }.readText()

    private fun ok(
        body: String,
        location: String? = null,
    ): GitHubReleaseHttpResponse = GitHubReleaseHttpResponse(statusCode = 200, body = body, location = location)

    private fun error(
        statusCode: Int,
        location: String? = null,
    ): GitHubReleaseHttpResponse =
        GitHubReleaseHttpResponse(
            statusCode = statusCode,
            body = """{"message":"request refused"}""",
            location = location,
        )
}

private class FakeGitHubTransport(
    private val respond: (GitHubReleaseHttpRequest) -> GitHubReleaseHttpResponse,
) : GitHubReleaseHttpTransport {
    val requestedUrls: MutableList<String> = mutableListOf()
    val requestedAccepts: MutableList<String> = mutableListOf()

    override suspend fun get(request: GitHubReleaseHttpRequest): GitHubReleaseHttpResponse {
        requestedUrls += request.url
        requestedAccepts += request.accept
        return respond(request)
    }
}

package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mercurio.client.feature.entry.domain.NightlyApk
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyTransfer
import org.mercurio.client.feature.entry.domain.NightlyTransferFailure
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

private val PAYLOAD = "mercurio-nightly-apk-bytes".toByteArray()

private const val ASSET_ADDRESS = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1"

private const val ASSET_LOCATION =
    "https://release-assets.githubusercontent.com/github-production-release-asset/1/mercurio.apk"

private const val CERTIFICATE_SHA = "2222222222222222222222222222222222222222222222222222222222222222"

class GitHubNightlyApkDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun streams_the_apk_into_the_private_directory_with_its_size_and_digest() =
        runTest {
            val download = download(FakeAssetTransport { _, sink -> accepted(sink, PAYLOAD) })

            val transfer = download.transfer(apk(), onProgress = {}) as NightlyTransfer.Transferred

            assertEquals(PAYLOAD.size.toLong(), transfer.artifact.sizeBytes)
            assertEquals(PAYLOAD.sha256(), transfer.artifact.sha256)
            assertEquals("nightly-update.apk", transfer.artifact.reference)
            assertTrue(File(folder.root, "nightly-update/nightly-update.apk").isFile)
        }

    @Test
    fun follows_an_allowlisted_redirect_and_writes_only_the_reached_asset() =
        runTest {
            val transport =
                FakeAssetTransport { request, sink ->
                    if (request.url == ASSET_ADDRESS) {
                        GitHubAssetStream(statusCode = 302, location = ASSET_LOCATION)
                    } else {
                        accepted(sink, PAYLOAD)
                    }
                }

            val transfer = download(transport).transfer(apk(), onProgress = {}) as NightlyTransfer.Transferred

            assertEquals(listOf(ASSET_ADDRESS, ASSET_LOCATION), transport.requestedUrls)
            assertEquals(PAYLOAD.sha256(), transfer.artifact.sha256)
        }

    @Test
    fun refuses_to_follow_a_redirect_outside_the_asset_host() =
        runTest {
            val transport =
                FakeAssetTransport { _, _ ->
                    GitHubAssetStream(statusCode = 302, location = "https://example.com/mercurio.apk")
                }

            val transfer = download(transport).transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.SERVICE_UNAVAILABLE), transfer)
            assertFalse(File(folder.root, "nightly-update.apk").exists())
        }

    @Test
    fun stops_at_the_first_byte_beyond_the_size_the_release_states() =
        runTest {
            val download = download(FakeAssetTransport { _, sink -> accepted(sink, PAYLOAD) })

            val transfer = download.transfer(apk(sizeBytes = PAYLOAD.size - 1L), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.PAYLOAD_TOO_LARGE), transfer)
            assertFalse(File(folder.root, "nightly-update.apk").exists())
        }

    @Test
    fun reports_progress_for_every_chunk_it_writes() =
        runTest {
            val first = PAYLOAD.copyOfRange(0, 8)
            val second = PAYLOAD.copyOfRange(8, PAYLOAD.size)
            val reported = mutableListOf<NightlyDownloadProgress>()

            download(FakeAssetTransport { _, sink -> accepted(sink, first, second) })
                .transfer(apk(), onProgress = { reported += it })

            assertEquals(
                listOf(
                    NightlyDownloadProgress(first.size.toLong(), PAYLOAD.size.toLong()),
                    NightlyDownloadProgress(PAYLOAD.size.toLong(), PAYLOAD.size.toLong()),
                ),
                reported,
            )
        }

    @Test
    fun reports_a_rate_limited_answer_as_a_rate_limited_transfer() =
        runTest {
            val transfer =
                download(FakeAssetTransport { _, _ -> GitHubAssetStream(statusCode = 429) })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.RATE_LIMITED), transfer)
        }

    @Test
    fun reports_a_server_refusal_as_a_service_failure() =
        runTest {
            val transfer =
                download(FakeAssetTransport { _, _ -> GitHubAssetStream(statusCode = 500) })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.SERVICE_UNAVAILABLE), transfer)
        }

    @Test
    fun reports_a_stalled_read_as_a_service_failure() =
        runTest {
            val transfer =
                download(FakeAssetTransport { _, _ -> throw SocketTimeoutException("stalled") })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.SERVICE_UNAVAILABLE), transfer)
        }

    @Test
    fun reports_an_unwritable_update_directory_as_storage_exhaustion() =
        runTest {
            val blocked = File(folder.root, "nightly-update")
            blocked.writeText("this path is a file, so the update directory cannot be created")

            val transfer =
                download(FakeAssetTransport { _, sink -> accepted(sink, PAYLOAD) })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.STORAGE_UNAVAILABLE), transfer)
        }

    @Test
    fun a_redirect_without_a_location_is_reported_as_a_service_failure() =
        runTest {
            val transfer =
                download(FakeAssetTransport { _, _ -> GitHubAssetStream(statusCode = 302) })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.SERVICE_UNAVAILABLE), transfer)
        }

    @Test
    fun discard_ignores_a_reference_that_escapes_the_update_directory() =
        runTest {
            val outside = File(folder.root, "outside.apk")
            outside.writeBytes(PAYLOAD)

            download(FakeAssetTransport { _, sink -> accepted(sink, PAYLOAD) })
                .discard(NightlyArtifact(reference = "../outside.apk", sizeBytes = 1L, sha256 = "0"))

            assertTrue(outside.exists())
        }

    @Test
    fun reports_an_unreachable_host_as_offline() =
        runTest {
            val transfer =
                download(FakeAssetTransport { _, _ -> throw UnknownHostException("github.com") })
                    .transfer(apk(), onProgress = {})

            assertEquals(NightlyTransfer.Failed(NightlyTransferFailure.OFFLINE), transfer)
        }

    @Test
    fun removes_its_partial_file_when_the_transfer_is_cancelled() {
        val download =
            download(
                FakeAssetTransport { _, sink ->
                    sink.write(PAYLOAD, 4)
                    throw CancellationException("cancelled")
                },
            )

        assertThrows(CancellationException::class.java) {
            runBlocking { download.transfer(apk(), onProgress = {}) }
        }
        assertFalse(File(folder.root, "nightly-update.apk").exists())
    }

    @Test
    fun discards_the_artifact_the_caller_will_not_use() =
        runTest {
            val download = download(FakeAssetTransport { _, sink -> accepted(sink, PAYLOAD) })
            val transfer = download.transfer(apk(), onProgress = {}) as NightlyTransfer.Transferred

            download.discard(transfer.artifact)

            assertFalse(File(folder.root, "nightly-update.apk").exists())
        }

    private fun download(transport: GitHubAssetHttpTransport): GitHubNightlyApkDownload =
        GitHubNightlyApkDownload(directory = File(folder.root, "nightly-update"), transport = transport)

    private fun apk(sizeBytes: Long = PAYLOAD.size.toLong()): NightlyApk =
        NightlyApk(
            address = ASSET_ADDRESS,
            sizeBytes = sizeBytes,
            sha256 = PAYLOAD.sha256(),
            certificateSha256 = CERTIFICATE_SHA,
            nativeAbis = listOf("arm64-v8a"),
        )
}

private class FakeAssetTransport(
    private val respond: (GitHubReleaseHttpRequest, GitHubAssetSink) -> GitHubAssetStream,
) : GitHubAssetHttpTransport {
    val requestedUrls: MutableList<String> = mutableListOf()

    override suspend fun stream(
        request: GitHubReleaseHttpRequest,
        sink: GitHubAssetSink,
    ): GitHubAssetStream {
        requestedUrls += request.url
        return respond(request, sink)
    }
}

private fun accepted(
    sink: GitHubAssetSink,
    vararg chunks: ByteArray,
): GitHubAssetStream {
    var written = 0L
    chunks.forEach { chunk ->
        sink.write(chunk, chunk.size)
        written += chunk.size
    }
    return GitHubAssetStream(statusCode = 200, byteCount = written)
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(radix = 16).padStart(length = 2, padChar = '0')
    }
}

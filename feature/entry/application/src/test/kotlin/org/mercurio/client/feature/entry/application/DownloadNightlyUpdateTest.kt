package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.NightlyApk
import org.mercurio.client.feature.entry.domain.NightlyApkDownload
import org.mercurio.client.feature.entry.domain.NightlyApkEvidence
import org.mercurio.client.feature.entry.domain.NightlyApkEvidenceSource
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyTransfer
import org.mercurio.client.feature.entry.domain.NightlyTransferFailure
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import java.time.Instant

private const val SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567"

private const val APK_SHA = "1111111111111111111111111111111111111111111111111111111111111111"

private const val CERTIFICATE_SHA = "2222222222222222222222222222222222222222222222222222222222222222"

private const val FOREIGN_CERTIFICATE_SHA = "3333333333333333333333333333333333333333333333333333333333333333"

private const val INSTALLED_CERTIFICATE_SHA = "4444444444444444444444444444444444444444444444444444444444444444"

private const val APK_BYTES = 13_002_342L

private const val VERSION_CODE = 7002L

private const val VERSION_NAME = "nightly-7002-0123456789ab"

private val PUBLISHED_AT = Instant.parse("2026-11-05T18:31:00Z")

private val VERIFIED_AT = Instant.parse("2026-11-05T19:41:00Z")

private val OFFER =
    NightlyUpdateOffer(
        releaseTag = "nightly-$VERSION_CODE-$SOURCE_SHA",
        versionName = VERSION_NAME,
        versionCode = VERSION_CODE,
        sourceCommit = SOURCE_SHA.take(7),
        publishedAt = PUBLISHED_AT,
        apk =
            NightlyApk(
                address = "https://api.github.com/repos/Gvetri/Mercurio/releases/assets/1",
                sizeBytes = APK_BYTES,
                sha256 = APK_SHA,
                certificateSha256 = CERTIFICATE_SHA,
                nativeAbis = listOf("arm64-v8a"),
            ),
    )

private val ARTIFACT = NightlyArtifact(reference = "nightly-update.apk", sizeBytes = APK_BYTES, sha256 = APK_SHA)

class DownloadNightlyUpdateTest {
    @Test
    fun verifies_an_archive_that_agrees_with_every_published_and_installed_value() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))

            val outcome = downloader(download, FakeNightlyApkEvidenceSource()).execute(OFFER)

            assertEquals(NightlyDownloadOutcome.Verified(VERIFIED_AT), outcome)
        }

    @Test
    fun removes_the_artifact_after_verification_because_this_stage_offers_no_install_path() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))

            downloader(download, FakeNightlyApkEvidenceSource()).execute(OFFER)

            assertEquals(listOf(ARTIFACT), download.discarded)
        }

    @Test
    fun refuses_an_archive_whose_byte_count_differs_from_the_release_before_any_platform_read() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT.copy(sizeBytes = APK_BYTES + 1)))
            val evidence = FakeNightlyApkEvidenceSource()

            val outcome = downloader(download, evidence).execute(OFFER)

            assertEquals(NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.SIZE_MISMATCH), outcome)
            assertEquals(0, evidence.reads)
            assertEquals(listOf(ARTIFACT.copy(sizeBytes = APK_BYTES + 1)), download.discarded)
        }

    @Test
    fun refuses_a_download_that_does_not_hash_to_the_published_digest_before_any_platform_read() =
        runTest {
            val tampered = ARTIFACT.copy(sha256 = "f".repeat(64))
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(tampered))
            val evidence = FakeNightlyApkEvidenceSource()

            val outcome = downloader(download, evidence).execute(OFFER)

            assertEquals(NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.HASH_MISMATCH), outcome)
            assertEquals(0, evidence.reads)
            assertEquals(listOf(tampered), download.discarded)
        }

    @Test
    fun refuses_an_archive_whose_embedded_version_contradicts_the_published_metadata() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val contradicting = verifiedArchive().copy(versionCode = VERSION_CODE + 1)
            val evidence = FakeNightlyApkEvidenceSource(archive = contradicting)

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.VERSION_MISMATCH),
                downloader(download, evidence).execute(OFFER),
            )
        }

    @Test
    fun refuses_an_archive_the_platform_cannot_read_as_an_apk() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.UNREADABLE_APK),
                downloader(download, FakeNightlyApkEvidenceSource(archive = null)).execute(OFFER),
            )
        }

    @Test
    fun refuses_an_archive_whose_certificate_contradicts_the_published_metadata() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val foreignSigner = verifiedArchive().copy(certificateSha256 = FOREIGN_CERTIFICATE_SHA)
            val evidence = FakeNightlyApkEvidenceSource(archive = foreignSigner)

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.SIGNER_MISMATCH),
                downloader(download, evidence).execute(OFFER),
            )
        }

    @Test
    fun refuses_an_archive_whose_certificate_contradicts_the_installed_application() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val evidence = FakeNightlyApkEvidenceSource(installedSigner = INSTALLED_CERTIFICATE_SHA)

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.SIGNER_MISMATCH),
                downloader(download, evidence).execute(OFFER),
            )
        }

    @Test
    fun refuses_an_archive_when_the_installed_signer_cannot_be_read() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val evidence = FakeNightlyApkEvidenceSource(installedSigner = null)

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.UNREADABLE_INSTALLED_SIGNER),
                downloader(download, evidence).execute(OFFER),
            )
        }

    @Test
    fun refuses_an_archive_that_carries_no_native_code_this_device_can_run() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val evidence =
                FakeNightlyApkEvidenceSource(archive = verifiedArchive().copy(nativeAbis = listOf("x86_64")))

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.ABI_MISMATCH),
                downloader(download, evidence).execute(OFFER),
            )
        }

    @Test
    fun reports_progress_while_the_transfer_runs() =
        runTest {
            val reported = mutableListOf<NightlyDownloadProgress>()
            val download = FakeNightlyApkDownload(NightlyTransfer.Transferred(ARTIFACT))
            val progress = listOf(NightlyDownloadProgress(0, APK_BYTES), NightlyDownloadProgress(APK_BYTES, APK_BYTES))

            downloader(download, FakeNightlyApkEvidenceSource()).execute(OFFER) { reported += it }

            assertEquals(progress, reported)
        }

    @Test
    fun reports_a_transfer_failure_without_reading_platform_evidence() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Failed(NightlyTransferFailure.OFFLINE))
            val evidence = FakeNightlyApkEvidenceSource()

            val outcome = downloader(download, evidence).execute(OFFER)

            assertEquals(NightlyDownloadOutcome.Failed(NightlyUpdateFailure.OFFLINE), outcome)
            assertEquals(0, evidence.reads)
            assertTrue(download.discarded.isEmpty())
        }

    @Test
    fun reports_a_refusal_when_the_answer_is_larger_than_the_release_states() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Failed(NightlyTransferFailure.PAYLOAD_TOO_LARGE))

            assertEquals(
                NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.SIZE_MISMATCH),
                downloader(download, FakeNightlyApkEvidenceSource()).execute(OFFER),
            )
        }

    @Test
    fun reports_storage_exhaustion_as_its_own_failure() =
        runTest {
            val download = FakeNightlyApkDownload(NightlyTransfer.Failed(NightlyTransferFailure.STORAGE_UNAVAILABLE))

            assertEquals(
                NightlyDownloadOutcome.Failed(NightlyUpdateFailure.STORAGE_UNAVAILABLE),
                downloader(download, FakeNightlyApkEvidenceSource()).execute(OFFER),
            )
        }

    private fun downloader(
        download: NightlyApkDownload,
        evidence: NightlyApkEvidenceSource,
    ): DownloadNightlyUpdate = DownloadNightlyUpdate(download = download, evidence = evidence, now = { VERIFIED_AT })
}

private fun verifiedArchive(): NightlyApkEvidence =
    NightlyApkEvidence(
        versionCode = VERSION_CODE,
        versionName = VERSION_NAME,
        certificateSha256 = CERTIFICATE_SHA,
        nativeAbis = listOf("arm64-v8a"),
    )

private class FakeNightlyApkDownload(
    private val outcome: NightlyTransfer,
) : NightlyApkDownload {
    val discarded: MutableList<NightlyArtifact> = mutableListOf()

    override suspend fun transfer(
        apk: NightlyApk,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ): NightlyTransfer {
        onProgress(NightlyDownloadProgress(0, apk.sizeBytes))
        onProgress(NightlyDownloadProgress(apk.sizeBytes, apk.sizeBytes))
        return outcome
    }

    override fun discard(artifact: NightlyArtifact) {
        discarded += artifact
    }
}

private class FakeNightlyApkEvidenceSource(
    private val archive: NightlyApkEvidence? = verifiedArchive(),
    private val installedSigner: String? = CERTIFICATE_SHA,
    private val abis: List<String> = listOf("arm64-v8a"),
) : NightlyApkEvidenceSource {
    var reads: Int = 0
        private set

    override fun apkEvidence(artifact: NightlyArtifact): NightlyApkEvidence? {
        reads++
        return archive
    }

    override fun installedSignerSha256(): String? = installedSigner

    override fun supportedAbis(): List<String> = abis
}

package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.NightlyApkDownload
import org.mercurio.client.feature.entry.domain.NightlyApkEvidence
import org.mercurio.client.feature.entry.domain.NightlyApkEvidenceSource
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyTransfer
import org.mercurio.client.feature.entry.domain.NightlyTransferFailure
import org.mercurio.client.feature.entry.domain.NightlyUpdateOffer
import java.time.Instant

/**
 * Downloads the offered Nightly and verifies it before anything else may use it: the byte count, the
 * published digest, the embedded version, the signing certificate against both the published metadata
 * and the installed application, and then the native ABI list. A refusal names its reason and removes
 * the artifact; a verified artifact is kept, because it is the install stage's input. The caller
 * discards a kept artifact through [discard] once the install stage reached a terminal result.
 */
class DownloadNightlyUpdate(
    private val download: NightlyApkDownload,
    private val evidence: NightlyApkEvidenceSource,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun execute(
        offer: NightlyUpdateOffer,
        onProgress: (NightlyDownloadProgress) -> Unit = {},
    ): NightlyDownloadOutcome =
        when (val transfer = download.transfer(offer.apk, onProgress)) {
            is NightlyTransfer.Failed -> transfer.reason.toOutcome()
            is NightlyTransfer.Transferred -> verifyThenHandOver(offer, transfer.artifact)
        }

    /** Removes a retained artifact the caller will not install. */
    fun discard(artifact: NightlyArtifact) {
        download.discard(artifact)
    }

    private fun verifyThenHandOver(
        offer: NightlyUpdateOffer,
        artifact: NightlyArtifact,
    ): NightlyDownloadOutcome {
        val refusal = refusalFor(offer, artifact)
        return if (refusal == null) {
            NightlyDownloadOutcome.Verified(artifact, now())
        } else {
            download.discard(artifact)
            NightlyDownloadOutcome.Refused(refusal)
        }
    }

    private fun refusalFor(
        offer: NightlyUpdateOffer,
        artifact: NightlyArtifact,
    ): NightlyDownloadRefusal? =
        when {
            artifact.sizeBytes != offer.apk.sizeBytes -> NightlyDownloadRefusal.SIZE_MISMATCH
            artifact.sha256 != offer.apk.sha256 -> NightlyDownloadRefusal.HASH_MISMATCH
            else -> platformRefusal(offer, artifact)
        }

    private fun platformRefusal(
        offer: NightlyUpdateOffer,
        artifact: NightlyArtifact,
    ): NightlyDownloadRefusal? {
        val read = evidence.apkEvidence(artifact) ?: return NightlyDownloadRefusal.UNREADABLE_APK
        return when {
            read.versionCode != offer.versionCode -> NightlyDownloadRefusal.VERSION_MISMATCH
            read.versionName != offer.versionName -> NightlyDownloadRefusal.VERSION_MISMATCH
            else -> signerAndAbiRefusal(offer, read)
        }
    }

    private fun signerAndAbiRefusal(
        offer: NightlyUpdateOffer,
        read: NightlyApkEvidence,
    ): NightlyDownloadRefusal? {
        val archivedSigner = read.certificateSha256
        val installedSigner = evidence.installedSignerSha256()
        return when {
            archivedSigner == null -> NightlyDownloadRefusal.UNREADABLE_APK
            installedSigner == null -> NightlyDownloadRefusal.UNREADABLE_INSTALLED_SIGNER
            !archivedSigner.equals(offer.apk.certificateSha256, ignoreCase = true) ->
                NightlyDownloadRefusal.SIGNER_MISMATCH
            !archivedSigner.equals(installedSigner, ignoreCase = true) ->
                NightlyDownloadRefusal.SIGNER_MISMATCH
            read.nativeAbis.none(evidence.supportedAbis()::contains) -> NightlyDownloadRefusal.ABI_MISMATCH
            else -> null
        }
    }
}

/** The outcome of one verified Nightly download. */
sealed interface NightlyDownloadOutcome {
    /** The archive agreed with every published and installed value, so it may be installed. */
    data class Verified(
        val artifact: NightlyArtifact,
        val verifiedAt: Instant,
    ) : NightlyDownloadOutcome

    /** The archive disagreed with a published or installed value, and was removed. */
    data class Refused(
        val reason: NightlyDownloadRefusal,
    ) : NightlyDownloadOutcome

    /** The transfer produced no archive, so no verification ran. */
    data class Failed(
        val reason: NightlyUpdateFailure,
    ) : NightlyDownloadOutcome
}

/** Why the download refused the offered archive. */
enum class NightlyDownloadRefusal {
    /** The transfer does not hold the byte count the release states. */
    SIZE_MISMATCH,

    /** The transfer does not hash to the digest the release states. */
    HASH_MISMATCH,

    /** The archive cannot be read as an APK. */
    UNREADABLE_APK,

    /** The archive's embedded version contradicts the published metadata. */
    VERSION_MISMATCH,

    /** The installed application's signing certificate cannot be read, so no archive can be trusted. */
    UNREADABLE_INSTALLED_SIGNER,

    /** The archive's signing certificate contradicts the published metadata or the installed application. */
    SIGNER_MISMATCH,

    /** The archive carries no native code this device can run. */
    ABI_MISMATCH,
}

private fun NightlyTransferFailure.toOutcome(): NightlyDownloadOutcome =
    when (this) {
        NightlyTransferFailure.PAYLOAD_TOO_LARGE ->
            NightlyDownloadOutcome.Refused(NightlyDownloadRefusal.SIZE_MISMATCH)
        NightlyTransferFailure.OFFLINE -> NightlyDownloadOutcome.Failed(NightlyUpdateFailure.OFFLINE)
        NightlyTransferFailure.RATE_LIMITED -> NightlyDownloadOutcome.Failed(NightlyUpdateFailure.RATE_LIMITED)
        NightlyTransferFailure.SERVICE_UNAVAILABLE ->
            NightlyDownloadOutcome.Failed(NightlyUpdateFailure.SERVICE_UNAVAILABLE)
        NightlyTransferFailure.STORAGE_UNAVAILABLE ->
            NightlyDownloadOutcome.Failed(NightlyUpdateFailure.STORAGE_UNAVAILABLE)
    }

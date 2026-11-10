package org.mercurio.client.feature.entry.domain

/** A downloaded APK on application-private storage, with the size and digest measured as it was written. */
data class NightlyArtifact(
    val reference: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** How far one Nightly APK transfer has come. */
data class NightlyDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
)

/** The transfer outcome: the stored artifact, or a typed failure. */
sealed interface NightlyTransfer {
    data class Transferred(
        val artifact: NightlyArtifact,
    ) : NightlyTransfer

    data class Failed(
        val reason: NightlyTransferFailure,
    ) : NightlyTransfer
}

/** Why a Nightly APK transfer produced no artifact. */
enum class NightlyTransferFailure {
    /** The network was unreachable or the host did not answer. */
    OFFLINE,

    /** The service refused the request as rate-limited. */
    RATE_LIMITED,

    /** The service answered with a status the update channel does not accept, or too late. */
    SERVICE_UNAVAILABLE,

    /** The private update directory could not hold the download. */
    STORAGE_UNAVAILABLE,

    /** The answer held more bytes than the release states, so the transfer stopped. */
    PAYLOAD_TOO_LARGE,
}

/**
 * Fetches one Nightly APK into application-private storage and reports progress while it writes.
 * A cancelled transfer removes its own partial file before it rethrows, and [discard] removes an
 * artifact the caller will not use.
 */
interface NightlyApkDownload {
    suspend fun transfer(
        apk: NightlyApk,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ): NightlyTransfer

    fun discard(artifact: NightlyArtifact)
}

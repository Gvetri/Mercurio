package org.mercurio.client.feature.entry.domain

import java.time.Instant

/**
 * Reads the published Nightly release channel: the release listing and the signing metadata of one
 * release. An implementation performs the network requests; the decision to offer a build stays in
 * the application layer.
 */
interface NightlyUpdateFeed {
    suspend fun publishedNightlies(): NightlyListing

    suspend fun signingMetadata(release: NightlyRelease): NightlySigningMetadataResult
}

/**
 * One published Nightly release that carries the channel's application APK, signing metadata, and
 * checksum asset, with the APK asset address and the size the release states for it.
 */
data class NightlyRelease(
    val tag: String,
    val publishedAt: Instant,
    val metadataAddress: String,
    val apkAddress: String,
    val apkSizeBytes: Long,
)

/** The listing outcome: the published Nightly releases, or a typed failure. */
sealed interface NightlyListing {
    data class Listed(
        val releases: List<NightlyRelease>,
    ) : NightlyListing

    data class Failed(
        val reason: NightlyFeedFailure,
    ) : NightlyListing
}

/** The signing-metadata document a Nightly publishes, as the release states it. */
data class NightlySigningMetadata(
    val sourceSha: String,
    val versionCode: Long,
    val versionName: String,
    val nativeAbis: List<String>,
    val apkSha256: String,
    val certificateSha256: String,
)

/** The metadata outcome: the parsed document, or a typed failure. */
sealed interface NightlySigningMetadataResult {
    data class Available(
        val metadata: NightlySigningMetadata,
    ) : NightlySigningMetadataResult

    data class Failed(
        val reason: NightlyFeedFailure,
    ) : NightlySigningMetadataResult
}

/** Why a feed request produced no document. */
enum class NightlyFeedFailure {
    /** The network was unreachable or the host did not answer. */
    OFFLINE,

    /** The service refused the request as rate-limited. */
    RATE_LIMITED,

    /** The service answered with a status the channel does not accept, or too late. */
    SERVICE_UNAVAILABLE,

    /** The response was not a document this channel can read. */
    MALFORMED_RESPONSE,
}

/**
 * True when the document contradicts the release that carries it: the tag, the version name, the
 * source commit, or a digest does not match the Nightly release contract.
 */
fun NightlySigningMetadata.contradicts(releaseTag: String): Boolean =
    !sourceSha.matches(SOURCE_SHA_PATTERN) ||
        versionCode <= 0L ||
        releaseTag != "nightly-$versionCode-$sourceSha" ||
        versionName != "nightly-$versionCode-${sourceSha.take(SHORT_SOURCE_SHA_LENGTH)}" ||
        !apkSha256.matches(DIGEST_PATTERN) ||
        !certificateSha256.matches(DIGEST_PATTERN)

/** The number of source-commit characters a Nightly version name carries. */
const val SHORT_SOURCE_SHA_LENGTH = 12

/** The number of source-commit characters a display identifier carries. */
const val SHORT_COMMIT_LENGTH = 7

private val SOURCE_SHA_PATTERN = Regex("[0-9a-f]{40}")

private val DIGEST_PATTERN = Regex("[0-9a-f]{64}")

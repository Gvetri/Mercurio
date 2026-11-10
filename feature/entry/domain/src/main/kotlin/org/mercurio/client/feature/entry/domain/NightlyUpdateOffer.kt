package org.mercurio.client.feature.entry.domain

import java.time.Instant

/**
 * The Nightly build one check offers, with everything a later download and verification needs.
 * The acquisition values are the release's own claim; nothing here is trusted until the downloaded
 * archive agrees with it.
 */
data class NightlyUpdateOffer(
    val releaseTag: String,
    val versionName: String,
    val versionCode: Long,
    val sourceCommit: String,
    val publishedAt: Instant,
    val apk: NightlyApk,
)

/** The application APK one Nightly release publishes, as the release and its metadata state it. */
data class NightlyApk(
    val address: String,
    val sizeBytes: Long,
    val sha256: String,
    val certificateSha256: String,
    val nativeAbis: List<String>,
)

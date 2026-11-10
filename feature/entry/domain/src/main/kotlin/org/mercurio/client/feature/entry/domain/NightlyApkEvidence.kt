package org.mercurio.client.feature.entry.domain

/** What the platform reads from one APK archive, beside what this installation states about itself. */
data class NightlyApkEvidence(
    val versionCode: Long,
    val versionName: String?,
    val certificateSha256: String?,
    val nativeAbis: List<String>,
)

/**
 * Reads the platform evidence a Nightly verification needs. None of it comes from the download, so
 * a tampered transfer cannot supply the values that would clear it.
 */
interface NightlyApkEvidenceSource {
    /** Reads the archive's own values, or null when the file cannot be read as an APK. */
    fun apkEvidence(artifact: NightlyArtifact): NightlyApkEvidence?

    /** The digest of the certificate that signed the installed application, or null when unreadable. */
    fun installedSignerSha256(): String?

    /** The ABIs this device runs. */
    fun supportedAbis(): List<String>
}

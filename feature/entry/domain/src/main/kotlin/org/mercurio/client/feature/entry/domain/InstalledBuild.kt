package org.mercurio.client.feature.entry.domain

enum class InstallSource {
    /** Google Play installed the client, so Play owns its updates. */
    GOOGLE_PLAY,

    /** Any other source installed the client: a Nightly APK, a file manager, or a direct install. */
    OTHER_SOURCE,
}

/**
 * Every build shares one climbing version-code sequence, so a newer build is a numeric
 * comparison against [versionCode].
 */
data class InstalledBuild(
    val versionCode: Long,
    val versionName: String,
    val installSource: InstallSource,
) {
    fun versionNameOr(fallback: String): String = versionName.takeIf(String::isNotBlank) ?: fallback
}

interface InstalledBuildProvider {
    fun installedBuild(): InstalledBuild
}

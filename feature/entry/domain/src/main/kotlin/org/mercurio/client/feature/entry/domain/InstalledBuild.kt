package org.mercurio.client.feature.entry.domain

/** The source that installed the client on this device. */
enum class InstallSource {
    /** Google Play installed the client, so Play owns its updates. */
    GOOGLE_PLAY,

    /** Any other source installed the client: a Nightly APK, a file manager, or a direct install. */
    OTHER_SOURCE,
}

/**
 * Identity of the client build installed on this device.
 *
 * [versionCode] and [versionName] are the values the platform reports for the client's own
 * package. Every build shares one climbing version-code sequence, so a newer build is a numeric
 * comparison against [versionCode], and [versionName] is the name the client reports about itself.
 * [installSource] records who installed the build, because a build Play distributed must not
 * update itself by any other route.
 */
data class InstalledBuild(
    val versionCode: Long,
    val versionName: String,
    val installSource: InstallSource,
) {
    /**
     * The version name to report, or [fallback] when the platform metadata carries none, so an
     * error report and a diagnostics export record a value instead of an empty field.
     */
    fun versionNameOr(fallback: String): String = versionName.takeIf(String::isNotBlank) ?: fallback
}

/** Reads the installed client's version code, version name, and install source. */
interface InstalledBuildProvider {
    fun installedBuild(): InstalledBuild
}

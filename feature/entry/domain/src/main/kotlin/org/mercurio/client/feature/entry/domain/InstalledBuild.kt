package org.mercurio.client.feature.entry.domain

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

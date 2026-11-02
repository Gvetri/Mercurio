package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

/** A fixed installed build a wiring test supplies instead of this installation's package metadata. */
internal class FakeInstalledBuildProvider(
    private val versionName: String,
    private val versionCode: Long = 1L,
    private val installSource: InstallSource = InstallSource.OTHER_SOURCE,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild =
        InstalledBuild(
            versionCode = versionCode,
            versionName = versionName,
            installSource = installSource,
        )
}

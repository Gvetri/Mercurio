package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

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

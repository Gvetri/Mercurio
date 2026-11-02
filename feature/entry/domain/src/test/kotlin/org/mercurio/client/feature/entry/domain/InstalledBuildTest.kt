package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledBuildTest {
    @Test
    fun reports_the_version_name_the_platform_reports() {
        val installedBuild =
            InstalledBuild(
                versionCode = 42L,
                versionName = "nightly-42-abc",
                installSource = InstallSource.OTHER_SOURCE,
            )

        assertEquals("nightly-42-abc", installedBuild.versionNameOr("unknown"))
    }

    @Test
    fun reports_the_fallback_when_the_platform_reports_no_version_name() {
        val installedBuild =
            InstalledBuild(
                versionCode = 42L,
                versionName = "",
                installSource = InstallSource.GOOGLE_PLAY,
            )

        assertEquals("unknown", installedBuild.versionNameOr("unknown"))
    }

    @Test
    fun reports_the_fallback_when_the_platform_reports_a_blank_version_name() {
        val installedBuild =
            InstalledBuild(
                versionCode = 42L,
                versionName = " ",
                installSource = InstallSource.GOOGLE_PLAY,
            )

        assertEquals("unknown", installedBuild.versionNameOr("unknown"))
    }
}

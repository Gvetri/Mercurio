package org.mercurio.client.feature.entry.wiring

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.InstallSource

class InstalledBuildMappingTest {
    @Test
    fun maps_a_play_install_to_the_google_play_source() {
        val installedBuild =
            installedBuildFrom(
                InstalledPackageMetadata(
                    versionCode = 412L,
                    versionName = "nightly-412-ab12cd",
                    installerPackageName = "com.android.vending",
                ),
            )

        assertEquals(412L, installedBuild.versionCode)
        assertEquals("nightly-412-ab12cd", installedBuild.versionName)
        assertEquals(InstallSource.GOOGLE_PLAY, installedBuild.installSource)
    }

    @Test
    fun maps_a_direct_install_without_an_installer_to_another_source() {
        val installedBuild =
            installedBuildFrom(
                InstalledPackageMetadata(
                    versionCode = 413L,
                    versionName = "nightly-413-ab12cd",
                    installerPackageName = null,
                ),
            )

        assertEquals(InstallSource.OTHER_SOURCE, installedBuild.installSource)
    }

    @Test
    fun maps_an_install_from_another_installer_to_another_source() {
        val installedBuild =
            installedBuildFrom(
                InstalledPackageMetadata(
                    versionCode = 414L,
                    versionName = "nightly-414-ab12cd",
                    installerPackageName = "com.example.filemanager",
                ),
            )

        assertEquals(InstallSource.OTHER_SOURCE, installedBuild.installSource)
    }

    @Test
    fun keeps_a_missing_version_name_as_the_platform_reports_it() {
        val installedBuild =
            installedBuildFrom(
                InstalledPackageMetadata(
                    versionCode = 415L,
                    versionName = null,
                    installerPackageName = "com.android.vending",
                ),
            )

        assertEquals("", installedBuild.versionName)
    }
}

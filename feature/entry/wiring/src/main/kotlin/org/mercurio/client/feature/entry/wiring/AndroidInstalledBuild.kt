package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

/** The package metadata the platform reports for the client's own package. */
internal data class InstalledPackageMetadata(
    val versionCode: Long,
    val versionName: String?,
    val installerPackageName: String?,
)

/** The installer package the platform records for a build Google Play manages. */
internal const val PLAY_INSTALLER_PACKAGE = "com.android.vending"

/** Maps the platform package metadata onto the installed build the domain reads. */
internal fun installedBuildFrom(metadata: InstalledPackageMetadata): InstalledBuild =
    InstalledBuild(
        versionCode = metadata.versionCode,
        versionName = metadata.versionName.orEmpty(),
        installSource =
            if (metadata.installerPackageName == PLAY_INSTALLER_PACKAGE) {
                InstallSource.GOOGLE_PLAY
            } else {
                InstallSource.OTHER_SOURCE
            },
    )

/** Reads the installed build from this installation's package metadata and recorded installer. */
class AndroidInstalledBuild(
    private val context: Context,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild = installedBuildFrom(packageMetadata())

    private fun packageMetadata(): InstalledPackageMetadata =
        runCatching {
            val packageInfo = installedPackageInfo()
            InstalledPackageMetadata(
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                versionName = packageInfo.versionName,
                installerPackageName = installerPackageName(context.packageManager),
            )
        }.getOrElse {
            InstalledPackageMetadata(versionCode = 0L, versionName = null, installerPackageName = null)
        }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    private fun installerPackageName(packageManager: PackageManager): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
}

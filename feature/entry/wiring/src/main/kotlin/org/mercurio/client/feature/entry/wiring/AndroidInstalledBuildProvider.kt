package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

internal data class InstalledPackageMetadata(
    val versionCode: Long,
    val versionName: String?,
    val installerPackageName: String?,
)

internal const val PLAY_INSTALLER_PACKAGE = "com.android.vending"

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

class AndroidInstalledBuildProvider(
    private val context: Context,
) : InstalledBuildProvider {
    override fun installedBuild(): InstalledBuild = installedBuildFrom(packageMetadata())

    private fun packageMetadata(): InstalledPackageMetadata =
        runCatching {
            val packageInfo = installedPackageInfo()
            InstalledPackageMetadata(
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                versionName = packageInfo.versionName,
                installerPackageName = installerPackageName(),
            )
        }.getOrElse {
            InstalledPackageMetadata(versionCode = 0L, versionName = null, installerPackageName = null)
        }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    private fun installerPackageName(): String? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
}

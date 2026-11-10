package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.mercurio.client.feature.entry.domain.NightlyApkEvidence
import org.mercurio.client.feature.entry.domain.NightlyApkEvidenceSource
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Reads the platform evidence a Nightly verification needs: the archive's embedded version and
 * signing certificate, the installed application's certificate, and the ABIs this device runs.
 * The archive values come from the platform's own package reader, never from the download's claim.
 */
class AndroidNightlyApkEvidenceSource(
    private val context: Context,
    private val directory: File,
) : NightlyApkEvidenceSource {
    override fun apkEvidence(artifact: NightlyArtifact): NightlyApkEvidence? =
        resolve(artifact)?.let { archive ->
            packageManager.getPackageArchiveInfo(archive.absolutePath, signingFlags())?.let { info ->
                NightlyApkEvidence(
                    versionCode = PackageInfoCompat.getLongVersionCode(info),
                    versionName = info.versionName,
                    certificateSha256 = certificateDigest(info),
                    nativeAbis = nativeAbis(archive),
                )
            }
        }

    override fun installedSignerSha256(): String? = installedPackageInfo()?.let(::certificateDigest)

    override fun supportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    private fun resolve(artifact: NightlyArtifact): File? {
        if (File(artifact.reference).name != artifact.reference) return null
        return File(directory, artifact.reference).takeIf(File::isFile)
    }

    private fun installedPackageInfo(): PackageInfo? =
        runCatching { packageManager.getPackageInfo(context.packageName, signingFlags()) }.getOrNull()

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun certificateDigest(info: PackageInfo): String? {
        val certificate = signers(info)?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray()).toHex()
    }

    private fun signers(info: PackageInfo): Array<android.content.pm.Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }

    private fun nativeAbis(archive: File): List<String> =
        runCatching {
            ZipFile(archive).use { zip ->
                zip.entries()
                    .asSequence()
                    .map { entry -> entry.name }
                    .filter { name -> name.startsWith(NATIVE_LIBRARY_PREFIX) }
                    .map { name -> name.removePrefix(NATIVE_LIBRARY_PREFIX).substringBefore('/') }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted()
                    .toList()
            }
        }.getOrDefault(emptyList())

    private val packageManager: PackageManager get() = context.packageManager

    private companion object {
        const val NATIVE_LIBRARY_PREFIX = "lib/"
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(radix = 16).padStart(length = 2, padChar = '0')
    }

private const val BYTE_MASK = 0xFF

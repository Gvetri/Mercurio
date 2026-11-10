package org.mercurio.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.wiring.AndroidNightlyApkEvidenceSource
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class NightlyApkEvidenceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val directory: File
        get() = File(context.cacheDir, DIRECTORY_NAME)

    private val evidence: AndroidNightlyApkEvidenceSource
        get() = AndroidNightlyApkEvidenceSource(context, directory)

    @Before
    fun prepareDirectory() {
        directory.deleteRecursively()
        check(directory.mkdirs()) { "Could not create the test update directory." }
    }

    @After
    fun removeDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun reads_the_version_and_certificate_of_the_installed_archive() {
        val archive = installedArchive()

        val read = evidence.apkEvidence(artifactOf(archive))

        assertNotNull(read)
        assertEquals(installedVersionCode(), read?.versionCode)
        assertEquals(installedVersionName(), read?.versionName)
        assertEquals(installedCertificateSha256(), read?.certificateSha256)
    }

    @Test
    fun reads_the_certificate_that_signed_the_installed_application() {
        assertEquals(installedCertificateSha256(), evidence.installedSignerSha256())
    }

    @Test
    fun reports_the_abis_this_device_runs() {
        assertEquals(Build.SUPPORTED_ABIS.toList(), evidence.supportedAbis())
    }

    @Test
    fun refuses_an_archive_that_is_not_a_readable_apk() {
        val archive = File(directory, "not-an-apk.apk")
        archive.writeBytes(ByteArray(size = JUNK_BYTES) { index -> (index % JUNK_MODULUS).toByte() })

        assertNull(evidence.apkEvidence(artifactOf(archive)))
    }

    @Test
    fun refuses_an_archive_that_was_truncated_after_signing() {
        val source = installedArchive()
        val bytes = source.readBytes()
        val truncated = File(directory, "truncated.apk")
        truncated.writeBytes(bytes.copyOfRange(0, bytes.size / HALF))

        assertNull(evidence.apkEvidence(artifactOf(truncated)))
    }

    private fun installedArchive(): File {
        val sourceDir = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        val copy = File(directory, "installed.apk")
        File(sourceDir).copyTo(copy, overwrite = true)
        return copy
    }

    private fun artifactOf(archive: File): NightlyArtifact =
        NightlyArtifact(reference = archive.name, sizeBytes = archive.length(), sha256 = archive.sha256())

    private fun installedVersionCode(): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    private fun installedVersionName(): String? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName
    }

    private fun installedCertificateSha256(): String? {
        val signature = installedSigner() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(signature).toHex()
    }

    @Suppress("DEPRECATION")
    private fun installedSigner(): ByteArray? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info =
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            return info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        }
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        return info.signatures?.firstOrNull()?.toByteArray()
    }

    private companion object {
        const val DIRECTORY_NAME = "nightly-update-test"
    }
}

private const val JUNK_BYTES = 2048

private const val JUNK_MODULUS = 251

private const val HALF = 2

private const val SHA256_BUFFER_BYTES = 16 * 1024

private const val BYTE_MASK = 0xFF

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(SHA256_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(radix = 16).padStart(length = 2, padChar = '0')
    }

package org.mercurio.client.feature.entry.wiring

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CancellationException
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyInstallOutcome
import org.mercurio.client.feature.entry.domain.NightlyInstallRefusal
import org.mercurio.client.feature.entry.domain.NightlyInstaller
import java.io.File
import java.io.IOException

/**
 * Stages a verified artifact in a platform install session and reports the terminal result. The
 * system installer screen performs the install: this adapter streams the bytes, commits the session,
 * launches the platform prompt when the platform asks for the user's action, and maps the status.
 * The probe reads the per-app install source permission before anything is staged.
 */
class AndroidNightlyInstaller(
    private val context: Context,
    private val directory: File,
) : NightlyInstaller {
    override fun installAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override suspend fun install(artifact: NightlyArtifact): NightlyInstallOutcome {
        val file = resolve(artifact)
        val installer = context.packageManager.packageInstaller
        val sessionId =
            file?.let {
                try {
                    installer.createSession(sessionParams())
                } catch (error: IOException) {
                    null
                }
            }
        if (file == null || sessionId == null) {
            return NightlyInstallOutcome.Refused(NightlyInstallRefusal.SYSTEM_REFUSED)
        }
        NightlyInstallStatusRelay.drain()
        return try {
            streamAndCommit(installer, sessionId, file, artifact)
            awaitOutcome()
        } catch (error: CancellationException) {
            abandonSession(installer, sessionId)
            throw error
        } catch (error: Exception) {
            abandonSession(installer, sessionId)
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.SYSTEM_REFUSED)
        }
    }

    private suspend fun awaitOutcome(): NightlyInstallOutcome {
        while (true) {
            val status = NightlyInstallStatusRelay.next()
            val code = status.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (code != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                return nightlyInstallOutcomeFor(code)
            }
            if (!launchUserAction(status)) {
                return NightlyInstallOutcome.Refused(NightlyInstallRefusal.SYSTEM_REFUSED)
            }
        }
    }

    @Suppress("DEPRECATION", "SwallowedException")
    private fun launchUserAction(status: Intent): Boolean {
        val prompt = status.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent ?: return false
        prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(prompt)
            true
        } catch (error: ActivityNotFoundException) {
            false
        } catch (error: SecurityException) {
            false
        }
    }

    private fun streamAndCommit(
        installer: PackageInstaller,
        sessionId: Int,
        file: File,
        artifact: NightlyArtifact,
    ) {
        installer.openSession(sessionId).use { session ->
            session.openWrite(STAGED_ARTIFACT_NAME, 0L, artifact.sizeBytes).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            session.commit(statusIntent().intentSender)
        }
    }

    private fun sessionParams(): PackageInstaller.SessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }

    private fun statusIntent(): PendingIntent {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            INSTALL_STATUS_REQUEST_CODE,
            Intent(context, NightlyInstallStatusReceiver::class.java),
            flags,
        )
    }

    private fun resolve(artifact: NightlyArtifact): File? {
        if (File(artifact.reference).name != artifact.reference) return null
        return File(directory, artifact.reference).takeIf(File::isFile)
    }

    private fun abandonSession(
        installer: PackageInstaller,
        sessionId: Int,
    ) {
        runCatching { installer.abandonSession(sessionId) }
    }

    private companion object {
        const val STAGED_ARTIFACT_NAME = "nightly-update.apk"

        const val INSTALL_STATUS_REQUEST_CODE = 0
    }
}

/**
 * The platform install outcome for one session status code: a success is an install, a storage
 * failure is the storage refusal, and every other terminal code is a named refusal.
 */
internal fun nightlyInstallOutcomeFor(status: Int): NightlyInstallOutcome =
    when (status) {
        PackageInstaller.STATUS_SUCCESS -> NightlyInstallOutcome.Installed
        PackageInstaller.STATUS_FAILURE_STORAGE ->
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.INSUFFICIENT_STORAGE)
        PackageInstaller.STATUS_FAILURE_ABORTED ->
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.CANCELLED)
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM)
        else -> NightlyInstallOutcome.Refused(NightlyInstallRefusal.SYSTEM_REFUSED)
    }

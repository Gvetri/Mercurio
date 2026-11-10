package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.application.CheckForNightlyUpdate
import org.mercurio.client.feature.entry.application.DownloadNightlyUpdate
import org.mercurio.client.feature.entry.data.GitHubNightlyApkDownload
import org.mercurio.client.feature.entry.data.GitHubNightlyUpdateFeed
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import java.io.File
import java.time.Instant

internal fun buildCheckForNightlyUpdate(
    overrides: EntryWiringOverrides,
    installedBuildProvider: InstalledBuildProvider,
): CheckForNightlyUpdate =
    CheckForNightlyUpdate(
        feed = overrides.nightlyUpdateFeed ?: GitHubNightlyUpdateFeed(),
        installedBuildProvider = installedBuildProvider,
        now = Instant::now,
    )

internal fun buildDownloadNightlyUpdate(
    context: Context,
    overrides: EntryWiringOverrides,
): DownloadNightlyUpdate {
    val directory = File(context.cacheDir, UPDATE_DIRECTORY_NAME)
    return DownloadNightlyUpdate(
        download = overrides.nightlyApkDownload ?: GitHubNightlyApkDownload(directory),
        evidence = overrides.nightlyApkEvidence ?: AndroidNightlyApkEvidenceSource(context, directory),
    )
}

private const val UPDATE_DIRECTORY_NAME = "nightly-update"

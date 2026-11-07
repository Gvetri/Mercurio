package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.application.CheckForNightlyUpdate
import org.mercurio.client.feature.entry.data.GitHubNightlyUpdateFeed
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
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

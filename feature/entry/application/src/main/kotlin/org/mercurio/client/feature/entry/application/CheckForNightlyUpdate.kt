package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider
import org.mercurio.client.feature.entry.domain.NightlyFeedFailure
import org.mercurio.client.feature.entry.domain.NightlyListing
import org.mercurio.client.feature.entry.domain.NightlyRelease
import org.mercurio.client.feature.entry.domain.NightlySigningMetadataResult
import org.mercurio.client.feature.entry.domain.NightlyUpdateFeed
import org.mercurio.client.feature.entry.domain.SHORT_COMMIT_LENGTH
import org.mercurio.client.feature.entry.domain.contradicts
import java.time.Instant

/**
 * One manual update check: read the published Nightlies, pick the newest one, refuse a document
 * that contradicts its release, and compare the offered version code with the installed build. A
 * check that cannot read the installed build refuses to offer anything, because a downgrade can
 * never be allowed.
 */
class CheckForNightlyUpdate(
    private val feed: NightlyUpdateFeed,
    private val installedBuildProvider: InstalledBuildProvider,
    private val now: () -> Instant,
) {
    suspend fun execute(): NightlyUpdateCheck {
        val installed = installedBuildProvider.installedBuild()
        if (installed.versionCode <= 0L) {
            return NightlyUpdateCheck.Refused(
                releaseTag = null,
                reason = NightlyUpdateRefusal.UNREADABLE_INSTALLED_BUILD,
            )
        }
        return when (val listing = feed.publishedNightlies()) {
            is NightlyListing.Failed -> NightlyUpdateCheck.Failed(listing.reason.toUpdateFailure())
            is NightlyListing.Listed -> evaluate(installed, listing.releases)
        }
    }

    private suspend fun evaluate(
        installed: InstalledBuild,
        releases: List<NightlyRelease>,
    ): NightlyUpdateCheck {
        val newest =
            releases.maxByOrNull(NightlyRelease::publishedAt)
                ?: return NightlyUpdateCheck.UpToDate(now())
        return when (val metadata = feed.signingMetadata(newest)) {
            is NightlySigningMetadataResult.Failed ->
                NightlyUpdateCheck.Failed(metadata.reason.toUpdateFailure())
            is NightlySigningMetadataResult.Available ->
                when {
                    metadata.metadata.contradicts(newest.tag) ->
                        NightlyUpdateCheck.Refused(newest.tag, NightlyUpdateRefusal.CONTRADICTORY_RELEASE)
                    metadata.metadata.versionCode <= installed.versionCode -> NightlyUpdateCheck.UpToDate(now())
                    else ->
                        NightlyUpdateCheck.Offered(
                            versionName = metadata.metadata.versionName,
                            sourceCommit = metadata.metadata.sourceSha.take(SHORT_COMMIT_LENGTH),
                            publishedAt = newest.publishedAt,
                            releaseTag = newest.tag,
                            checkedAt = now(),
                        )
                }
        }
    }
}

/** The outcome of one manual Nightly update check. */
sealed interface NightlyUpdateCheck {
    data class UpToDate(
        val checkedAt: Instant,
    ) : NightlyUpdateCheck

    data class Offered(
        val versionName: String,
        val sourceCommit: String,
        val publishedAt: Instant,
        val releaseTag: String,
        val checkedAt: Instant,
    ) : NightlyUpdateCheck

    data class Refused(
        val releaseTag: String?,
        val reason: NightlyUpdateRefusal,
    ) : NightlyUpdateCheck

    data class Failed(
        val reason: NightlyUpdateFailure,
    ) : NightlyUpdateCheck
}

/** Why the check refused the offered build. */
enum class NightlyUpdateRefusal {
    /** The release carries the channel's assets but its metadata contradicts them. */
    CONTRADICTORY_RELEASE,

    /** The installed build reports no readable version code, so no offer can be compared. */
    UNREADABLE_INSTALLED_BUILD,
}

/** Why the check produced no result. */
enum class NightlyUpdateFailure {
    OFFLINE,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
}

private fun NightlyFeedFailure.toUpdateFailure(): NightlyUpdateFailure =
    when (this) {
        NightlyFeedFailure.OFFLINE -> NightlyUpdateFailure.OFFLINE
        NightlyFeedFailure.RATE_LIMITED -> NightlyUpdateFailure.RATE_LIMITED
        NightlyFeedFailure.SERVICE_UNAVAILABLE,
        NightlyFeedFailure.MALFORMED_RESPONSE,
        -> NightlyUpdateFailure.SERVICE_UNAVAILABLE
    }

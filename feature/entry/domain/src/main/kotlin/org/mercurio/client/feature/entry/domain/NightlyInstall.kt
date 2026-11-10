package org.mercurio.client.feature.entry.domain

/**
 * Hands a verified Nightly artifact to the platform installer stage: the bytes are streamed into an
 * install session and the platform's own confirmation decides the outcome. The permission probe
 * runs before anything is staged, so a missing permission never leaves staged data behind.
 */
interface NightlyInstaller {
    /** True when the platform currently lets this app install packages. */
    fun installAllowed(): Boolean

    /**
     * Streams an artifact that already passed verification into an install session and reports the
     * terminal result, launching the system installer when the platform asks for the user's action.
     */
    suspend fun install(artifact: NightlyArtifact): NightlyInstallOutcome
}

/** How one install attempt ended. */
sealed interface NightlyInstallOutcome {
    /** The platform installed the update; the running process still holds the previous version. */
    data object Installed : NightlyInstallOutcome

    /** The platform installed nothing, and the reason names what happened. */
    data class Refused(
        val reason: NightlyInstallRefusal,
    ) : NightlyInstallOutcome

    /** The per-app install permission is missing, so nothing was staged. */
    data object PermissionRequired : NightlyInstallOutcome
}

/** Why the platform installed nothing. */
enum class NightlyInstallRefusal {
    /** The install was cancelled before it finished. */
    CANCELLED,

    /** Android's install policy blocked the install. */
    BLOCKED_BY_SYSTEM,

    /** The platform refused the install for a reason it does not name more precisely. */
    SYSTEM_REFUSED,

    /** The install could not be stored for lack of space. */
    INSUFFICIENT_STORAGE,
}

package org.mercurio.client.feature.entry.domain

/** Platform permission status for Run status notifications. */
interface RunStatusNotificationPermission {
    /** True when the client may currently post a notification. */
    fun canPost(): Boolean

    /**
     * True when the platform requires a runtime permission request before a
     * notification can post, and that permission is not granted yet.
     */
    fun requiresRuntimePermissionRequest(): Boolean
}

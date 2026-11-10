package org.mercurio.client.feature.entry.wiring

/**
 * Host callbacks the entry wiring hands to the presentation layer. The activity owns the
 * permission requests and the restart, so the state holder only reports that the action is wanted.
 */
data class EntryHostActions(
    val requestRunStatusNotificationPermission: (() -> Unit)? = null,
    val requestAppUnlock: (() -> Unit)? = null,
    val requestInstallPermissionSettings: (() -> Unit)? = null,
    val requestAppRestart: (() -> Unit)? = null,
)

package org.mercurio.client.feature.entry.domain

/** Terminal outcomes that may produce a best-effort Run status notification. */
val notifiableTerminalStates: Set<RunPresentationState> =
    setOf(RunPresentationState.SUCCEEDED, RunPresentationState.FAILED)

/** Terminal outcomes that may produce a best-effort Run status notification. */
fun RunPresentationState.isNotifiableTerminal(): Boolean = this in notifiableTerminalStates

/**
 * Decides whether a terminal observed Run may notify.
 *
 * A notification requires the persisted user preference to be enabled, the
 * platform permission to allow posting, and a notifiable terminal state.
 */
fun shouldPostRunStatusNotification(
    enabled: Boolean,
    canPost: Boolean,
    state: RunPresentationState,
): Boolean = enabled && canPost && state.isNotifiableTerminal()

/** Persisted user preference for Run status notifications. */
interface RunStatusNotificationSettingsStore {
    fun loadEnabled(): Boolean

    fun saveEnabled(enabled: Boolean)
}

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

/** Best-effort poster for terminal Run status notifications. */
interface RunStatusNotifier {
    /**
     * Posts at most one notification for [run] in [state].
     *
     * Implementations must derive all content from [state]; conversation data,
     * credentials, headers, tool data, and endpoint data must never be included.
     */
    fun postTerminal(
        run: Run,
        state: RunPresentationState,
    )
}

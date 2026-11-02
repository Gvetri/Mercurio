package org.mercurio.client.feature.entry.domain

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

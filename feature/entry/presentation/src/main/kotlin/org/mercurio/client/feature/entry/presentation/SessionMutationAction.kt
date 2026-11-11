package org.mercurio.client.feature.entry.presentation

enum class SessionMutationAction {
    RENAME,
    PIN,
    UNPIN,
    DELETE,
}

internal fun SessionMutationAction.pendingLabelRes(): Int =
    when (this) {
        SessionMutationAction.RENAME -> R.string.sessions_renaming
        SessionMutationAction.PIN -> R.string.sessions_pinning
        SessionMutationAction.UNPIN -> R.string.sessions_unpinning
        SessionMutationAction.DELETE -> R.string.sessions_deleting
    }

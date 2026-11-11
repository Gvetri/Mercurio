package org.mercurio.client.feature.entry.presentation

/** The run line's right segment: the Gateway state, or the one offered action. */
internal sealed interface ChatRunRight {
    data class Gateway(
        val online: Boolean,
    ) : ChatRunRight

    data class Action(
        val label: UiText,
        val event: EntryUiEvent,
    ) : ChatRunRight
}

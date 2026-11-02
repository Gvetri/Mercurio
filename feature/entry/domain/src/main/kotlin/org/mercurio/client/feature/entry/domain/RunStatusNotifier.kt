package org.mercurio.client.feature.entry.domain

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

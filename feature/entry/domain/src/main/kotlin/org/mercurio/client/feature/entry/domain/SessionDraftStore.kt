package org.mercurio.client.feature.entry.domain

/**
 * Persists the composer draft text for one Session so a failed submission or an app
 * restart can restore what the user typed. Implementations store only that text for
 * the addressed Session; nothing else is ever written.
 */
interface SessionDraftStore {
    fun read(key: SessionDraftKey): String?

    fun write(
        key: SessionDraftKey,
        text: String,
    )

    fun remove(key: SessionDraftKey)

    fun clearEndpoint(endpoint: String) = Unit
}

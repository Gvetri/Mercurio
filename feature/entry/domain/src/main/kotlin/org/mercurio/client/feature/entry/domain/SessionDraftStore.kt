package org.mercurio.client.feature.entry.domain

/** The Gateway endpoint and Session a persisted composer draft belongs to. */
data class SessionDraftKey(
    val endpoint: String,
    val sessionId: SessionId,
)

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

/** In-memory default used when no persisted draft store is provided. */
object NoOpSessionDraftStore : SessionDraftStore {
    override fun read(key: SessionDraftKey): String? = null

    override fun write(
        key: SessionDraftKey,
        text: String,
    ) = Unit

    override fun remove(key: SessionDraftKey) = Unit
}

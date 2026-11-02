package org.mercurio.client.feature.entry.domain

/** The longest external destination a link or a Message image may name. */
const val MAX_EXTERNAL_DESTINATION_LENGTH = 2_048

/**
 * Fetches the bytes of one image addressed by untrusted Message content.
 *
 * The port is the only path from rendering to an image byte stream. An implementation enforces
 * the HTTPS, address, redirect, content-type, size, timeout, and cancellation rules, so no
 * renderer reaches an HTTP stack and every refusal is a typed outcome instead of an exception.
 */
interface MessageImagePort {
    suspend fun fetch(destination: String): MessageImageFetch
}

/** One fetch outcome: the accepted bytes with their media type, or the refusal reason. */
sealed interface MessageImageFetch {
    data class Fetched(
        val bytes: ByteArray,
        val contentType: String,
    ) : MessageImageFetch

    data class Refused(
        val reason: MessageImageRefusal,
    ) : MessageImageFetch
}

/** Why a Message image was not fetched. */
enum class MessageImageRefusal {
    /** The destination is not an absolute HTTPS URL, or a redirect left HTTPS. */
    UNSAFE_DESTINATION,

    /** The host, or one address it resolves to, is private, loopback, link-local, or reserved. */
    UNSAFE_ADDRESS,

    /** The response body was empty. */
    EMPTY_RESPONSE,

    /** The response media type was not an image type. */
    UNSUPPORTED_CONTENT_TYPE,

    /** The response body exceeded the bounded size. */
    TOO_LARGE,

    /** The transport failed, or the server returned a non-success status. */
    NETWORK,

    /** The transport timed out. */
    TIMEOUT,
}

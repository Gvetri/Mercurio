package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mercurio.client.feature.entry.domain.MAX_EXTERNAL_DESTINATION_LENGTH
import org.mercurio.client.feature.entry.domain.MessageImageFetch
import org.mercurio.client.feature.entry.domain.MessageImagePort
import org.mercurio.client.feature.entry.domain.MessageImageRefusal
import java.io.IOException

/**
 * Fetches Message images behind the closed guardrail set: an absolute HTTPS destination, an HTTPS
 * redirect chain of bounded length, a public unicast address on every hop, an image media type, a
 * bounded body, and a typed refusal for every failure. Nothing is written to disk.
 */
class HttpMessageImagePort(
    private val transport: MessageImageHttpTransport,
    private val hostResolver: MessageImageHostResolver = SystemMessageImageHostResolver(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MessageImagePort {
    override suspend fun fetch(destination: String): MessageImageFetch =
        withContext(dispatcher) {
            val url = parseHttpsUrl(destination)
            if (url == null) {
                MessageImageFetch.Refused(MessageImageRefusal.UNSAFE_DESTINATION)
            } else {
                fetchFollowingRedirects(url)
            }
        }

    private suspend fun fetchFollowingRedirects(start: HttpUrl): MessageImageFetch {
        var current = start
        var redirects = 0
        while (true) {
            when (val hop = fetchHop(current)) {
                is Hop.Final -> return hop.fetch
                is Hop.Redirect ->
                    if (redirects >= MAX_REDIRECTS) {
                        return MessageImageFetch.Refused(MessageImageRefusal.NETWORK)
                    } else {
                        current = hop.target
                        redirects++
                    }
            }
        }
    }

    private suspend fun fetchHop(url: HttpUrl): Hop =
        when (val refusal = addressRefusal(url.host)) {
            null -> executeHop(url)
            else -> Hop.Final(MessageImageFetch.Refused(refusal))
        }

    private suspend fun executeHop(url: HttpUrl): Hop {
        val response =
            try {
                transport.execute(MessageImageHttpRequest(url.toString()))
            } catch (error: MessageImageTransportException) {
                return Hop.Final(MessageImageFetch.Refused(error.kind.toRefusal()))
            }
        return response.toHop(url)
    }

    @Suppress("SwallowedException")
    private suspend fun addressRefusal(host: String): MessageImageRefusal? {
        val addresses =
            try {
                hostResolver.resolve(host)
            } catch (error: IOException) {
                return MessageImageRefusal.NETWORK
            }
        return if (addresses.isEmpty() || addresses.any { !isPublicMessageImageAddress(it) }) {
            MessageImageRefusal.UNSAFE_ADDRESS
        } else {
            null
        }
    }
}

private sealed interface Hop {
    data class Final(
        val fetch: MessageImageFetch,
    ) : Hop

    data class Redirect(
        val target: HttpUrl,
    ) : Hop
}

private fun parseHttpsUrl(destination: String): HttpUrl? {
    val trimmed = destination.trim()
    val hasUnsafeCharacters = trimmed.any { it.isWhitespace() || it.isISOControl() || it == '\\' }
    val url = trimmed.takeIf { !hasUnsafeCharacters && it.length <= MAX_EXTERNAL_DESTINATION_LENGTH }?.toHttpUrlOrNull()
    return url?.takeIf { it.scheme == HTTPS_SCHEME && it.username.isEmpty() && it.password.isEmpty() }
}

private fun resolveRedirect(target: HttpUrl): HttpUrl? =
    target.takeIf { it.scheme == HTTPS_SCHEME && it.username.isEmpty() && it.password.isEmpty() }

private fun redirectRefusal(location: String?): MessageImageRefusal =
    if (location == null) MessageImageRefusal.NETWORK else MessageImageRefusal.UNSAFE_DESTINATION

private fun MessageImageHttpResponse.toHop(current: HttpUrl): Hop =
    when {
        statusCode in REDIRECT_STATUSES ->
            location?.let(current::resolve)
                ?.let { target -> resolveRedirect(target) }
                ?.let(Hop::Redirect)
                ?: Hop.Final(MessageImageFetch.Refused(redirectRefusal(location)))
        statusCode != HTTP_OK -> Hop.Final(MessageImageFetch.Refused(MessageImageRefusal.NETWORK))
        body.isEmpty() -> Hop.Final(MessageImageFetch.Refused(MessageImageRefusal.EMPTY_RESPONSE))
        !contentType.isImageMediaType() ->
            Hop.Final(MessageImageFetch.Refused(MessageImageRefusal.UNSUPPORTED_CONTENT_TYPE))
        else -> Hop.Final(MessageImageFetch.Fetched(body, contentType.orEmpty()))
    }

private fun String?.isImageMediaType(): Boolean {
    val mediaType = this?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return mediaType.startsWith(IMAGE_MEDIA_TYPE_PREFIX) && mediaType.length > IMAGE_MEDIA_TYPE_PREFIX.length
}

private fun MessageImageTransportException.Kind.toRefusal(): MessageImageRefusal =
    when (this) {
        MessageImageTransportException.Kind.NETWORK -> MessageImageRefusal.NETWORK
        MessageImageTransportException.Kind.TIMEOUT -> MessageImageRefusal.TIMEOUT
        MessageImageTransportException.Kind.TOO_LARGE -> MessageImageRefusal.TOO_LARGE
        MessageImageTransportException.Kind.UNSAFE_ADDRESS -> MessageImageRefusal.UNSAFE_ADDRESS
    }

private const val HTTPS_SCHEME = "https"

private const val MAX_REDIRECTS = 3

private const val HTTP_OK = 200

@Suppress("MagicNumber")
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

private const val IMAGE_MEDIA_TYPE_PREFIX = "image/"

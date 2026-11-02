package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One image request: an absolute URL the port already accepted. */
class MessageImageHttpRequest(
    val url: String,
)

/**
 * One transport answer. The body is bounded by the transport, and a redirect is reported as a
 * status and a location instead of being followed.
 */
class MessageImageHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val location: String?,
    val body: ByteArray,
)

/** The byte-level boundary the image port drives; a test double replaces it. */
interface MessageImageHttpTransport {
    suspend fun execute(request: MessageImageHttpRequest): MessageImageHttpResponse
}

/** A transport failure the port maps to a refusal instead of propagating. */
class MessageImageTransportException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Kind {
        NETWORK,
        TIMEOUT,
        TOO_LARGE,
        UNSAFE_ADDRESS,
    }
}

/** The transport the application uses outside tests, constructed without exposing OkHttp to a caller. */
fun defaultMessageImageHttpTransport(): MessageImageHttpTransport = OkHttpMessageImageHttpTransport()

/**
 * The OkHttp implementation: redirects are not followed so the port can require HTTPS on every
 * hop, the body is read under a hard byte cap, a timeout is reported as its own failure, and a
 * connection whose acquired socket is not public unicast is cancelled before the request is
 * written.
 */
class OkHttpMessageImageHttpTransport(
    client: OkHttpClient = OkHttpClient(),
    private val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
    private val addressPolicy: (String) -> Boolean = ::isPublicMessageImageAddress,
) : MessageImageHttpTransport {
    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(CALL_TIMEOUT)
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .build()

    override suspend fun execute(request: MessageImageHttpRequest): MessageImageHttpResponse =
        suspendCancellableCoroutine { continuation ->
            var connectedAddress: InetAddress? = null
            var addressRefused = false

            fun checkConnectedAddress(
                call: Call,
                address: InetAddress,
            ) {
                connectedAddress = address
                if (!addressPolicy(address.hostAddress.orEmpty())) {
                    addressRefused = true
                    call.cancel()
                }
            }

            val listener =
                object : EventListener() {
                    override fun connectionAcquired(
                        call: Call,
                        connection: Connection,
                    ) {
                        checkConnectedAddress(call, connection.route().socketAddress.address)
                    }

                    override fun connectEnd(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                        protocol: Protocol?,
                    ) {
                        checkConnectedAddress(call, inetSocketAddress.address)
                    }
                }
            val callClient = client.newBuilder().eventListener(listener).build()
            val call = callClient.newCall(Request.Builder().url(request.url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        exception: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                if (addressRefused) unsafeAddressFailure() else exception.toTransportException(),
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            response.use {
                                val address = connectedAddress
                                if (address == null || !addressPolicy(address.hostAddress.orEmpty())) {
                                    throw unsafeAddressFailure()
                                }
                                val imageResponse = it.toImageResponse()
                                if (continuation.isActive) continuation.resume(imageResponse)
                            }
                        } catch (exception: MessageImageTransportException) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        } catch (exception: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception.toTransportException())
                            }
                        }
                    }
                },
            )
        }

    private fun Response.toImageResponse(): MessageImageHttpResponse =
        MessageImageHttpResponse(
            statusCode = code,
            contentType = header("Content-Type"),
            location = header("Location"),
            body = readBoundedBody(),
        )

    private fun Response.readBoundedBody(): ByteArray {
        val source = body.byteStream()
        val declaredLength = body.contentLength()
        if (declaredLength > maxBodyBytes) throw tooLarge()
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = source.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBodyBytes) throw tooLarge()
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun tooLarge(): MessageImageTransportException =
        MessageImageTransportException(
            MessageImageTransportException.Kind.TOO_LARGE,
            "The image response exceeded $maxBodyBytes bytes.",
        )

    private companion object {
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(15)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)

        const val DEFAULT_MAX_BODY_BYTES = 8 * 1024 * 1024

        const val READ_CHUNK_BYTES = 8 * 1024
    }
}

private fun unsafeAddressFailure(): MessageImageTransportException =
    MessageImageTransportException(
        MessageImageTransportException.Kind.UNSAFE_ADDRESS,
        "The image connection did not reach a public unicast address.",
    )

private fun IOException.toTransportException(): MessageImageTransportException =
    MessageImageTransportException(
        kind =
            if (this is InterruptedIOException) {
                MessageImageTransportException.Kind.TIMEOUT
            } else {
                MessageImageTransportException.Kind.NETWORK
            },
        message = message ?: "The image transport failed.",
        cause = this,
    )

package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.mercurio.client.feature.entry.domain.NightlyApk
import org.mercurio.client.feature.entry.domain.NightlyApkDownload
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyDownloadProgress
import org.mercurio.client.feature.entry.domain.NightlyTransfer
import org.mercurio.client.feature.entry.domain.NightlyTransferFailure
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One streaming GitHub asset answer: the status, the redirect target if any, and the bytes written. */
class GitHubAssetStream(
    val statusCode: Int,
    val location: String? = null,
    val byteCount: Long = 0L,
)

/** The body sink a streaming asset download writes into. */
fun interface GitHubAssetSink {
    fun write(
        buffer: ByteArray,
        length: Int,
    )
}

/**
 * Streams one GitHub asset answer into a sink. The sink receives bytes only for a successful answer,
 * so a redirect answer never reaches the destination file.
 */
interface GitHubAssetHttpTransport {
    suspend fun stream(
        request: GitHubReleaseHttpRequest,
        sink: GitHubAssetSink,
    ): GitHubAssetStream
}

/** The streaming transport the application uses outside tests. */
fun defaultGitHubAssetHttpTransport(): GitHubAssetHttpTransport = OkHttpGitHubAssetHttpTransport()

/** The OkHttp implementation: one GET, no redirect, a chunked copy, and cancellation that cancels the call. */
class OkHttpGitHubAssetHttpTransport(
    client: OkHttpClient = OkHttpClient(),
) : GitHubAssetHttpTransport {
    private val client =
        client
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    override suspend fun stream(
        request: GitHubReleaseHttpRequest,
        sink: GitHubAssetSink,
    ): GitHubAssetStream =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request(request))
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val answer = runCatching { response.use { it.copyTo(sink) } }
                        answer.fold(
                            onSuccess = { value -> if (continuation.isActive) continuation.resume(value) },
                            onFailure = { error ->
                                call.cancel()
                                if (continuation.isActive) continuation.resumeWithException(error)
                            },
                        )
                    }
                },
            )
        }

    private fun request(request: GitHubReleaseHttpRequest): Request =
        Request
            .Builder()
            .url(request.url)
            .header("Accept", request.accept)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

    private fun Response.copyTo(sink: GitHubAssetSink): GitHubAssetStream {
        if (code != HTTP_OK) return GitHubAssetStream(statusCode = code, location = header("Location"))
        var written = 0L
        body.source().use { source ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                sink.write(buffer, read)
                written += read
            }
        }
        return GitHubAssetStream(statusCode = code, byteCount = written)
    }

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT = "Mercurio"
        const val CALL_TIMEOUT_SECONDS = 300L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 30L
        const val HTTP_OK = 200
        const val CHUNK_BYTES = 16 * 1024
    }
}

/**
 * Downloads one Nightly APK into the private update directory, hashing the stored bytes, and removes
 * artifacts the caller will not use. The transfer stops at the first byte beyond the size the release
 * states, so a wrong answer cannot fill the device.
 */
class GitHubNightlyApkDownload(
    private val directory: File,
    private val transport: GitHubAssetHttpTransport = defaultGitHubAssetHttpTransport(),
) : NightlyApkDownload {
    override suspend fun transfer(
        apk: NightlyApk,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ): NightlyTransfer {
        val target = File(directory, ARTIFACT_FILE_NAME)
        if (!directory.isDirectory && !directory.mkdirs()) {
            return NightlyTransfer.Failed(NightlyTransferFailure.STORAGE_UNAVAILABLE)
        }
        return try {
            write(apk, target, onProgress)
            NightlyTransfer.Transferred(
                NightlyArtifact(reference = target.name, sizeBytes = target.length(), sha256 = target.sha256()),
            )
        } catch (error: CancellationException) {
            target.delete()
            throw error
        } catch (_: PayloadTooLargeException) {
            target.delete()
            NightlyTransfer.Failed(NightlyTransferFailure.PAYLOAD_TOO_LARGE)
        } catch (_: UpdateStorageException) {
            target.delete()
            NightlyTransfer.Failed(NightlyTransferFailure.STORAGE_UNAVAILABLE)
        } catch (error: IOException) {
            target.delete()
            NightlyTransfer.Failed(error.toTransferFailure())
        }
    }

    override fun discard(artifact: NightlyArtifact) {
        if (File(artifact.reference).name != artifact.reference) return
        File(directory, artifact.reference).delete()
    }

    private suspend fun write(
        apk: NightlyApk,
        target: File,
        onProgress: (NightlyDownloadProgress) -> Unit,
    ) {
        var written = 0L
        FileOutputStream(target).use { output ->
            val sink =
                GitHubAssetSink { buffer, length ->
                    if (written + length > apk.sizeBytes) throw PayloadTooLargeException()
                    output.writeChunk(buffer, length)
                    written += length
                    onProgress(NightlyDownloadProgress(written, apk.sizeBytes))
                }
            val answer = followRedirect(GitHubReleaseHttpRequest(apk.address, ACCEPT), sink)
            if (answer.statusCode != HTTP_OK) throw AssetRefusedException(answer.statusCode)
        }
    }

    private suspend fun followRedirect(
        request: GitHubReleaseHttpRequest,
        sink: GitHubAssetSink,
    ): GitHubAssetStream {
        val answer = transport.stream(request, sink)
        val target = answer.location
        if (answer.statusCode != HTTP_REDIRECT || target == null || !target.isAllowlistedAssetLocation()) {
            return answer
        }
        return transport.stream(GitHubReleaseHttpRequest(target, ACCEPT), sink)
    }

    private fun FileOutputStream.writeChunk(
        buffer: ByteArray,
        length: Int,
    ) {
        try {
            write(buffer, 0, length)
        } catch (error: IOException) {
            throw UpdateStorageException(error)
        }
    }

    private companion object {
        const val ACCEPT = GitHubReleaseHttpRequest.GITHUB_ASSET_MEDIA_TYPE
        const val ARTIFACT_FILE_NAME = "nightly-update.apk"
        const val HTTP_OK = 200
        const val HTTP_REDIRECT = 302
    }
}

private class PayloadTooLargeException : IOException("The answer exceeded the size the release states")

private class UpdateStorageException(
    cause: IOException,
) : IOException(cause)

private class AssetRefusedException(
    private val statusCode: Int,
) : IOException("GitHub refused the asset request with status $statusCode") {
    fun refusedFailure(): NightlyTransferFailure =
        when (statusCode) {
            HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> NightlyTransferFailure.RATE_LIMITED
            else -> NightlyTransferFailure.SERVICE_UNAVAILABLE
        }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private fun IOException.toTransferFailure(): NightlyTransferFailure =
    when (this) {
        is AssetRefusedException -> refusedFailure()
        is UnknownHostException -> NightlyTransferFailure.OFFLINE
        is InterruptedIOException -> NightlyTransferFailure.SERVICE_UNAVAILABLE
        else -> NightlyTransferFailure.OFFLINE
    }

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(SHA256_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte ->
        (byte.toInt() and BYTE_MASK).toString(radix = 16).padStart(length = 2, padChar = '0')
    }
}

private const val SHA256_BUFFER_BYTES = 16 * 1024

private const val BYTE_MASK = 0xFF

package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.mercurio.client.feature.entry.domain.NightlyFeedFailure
import org.mercurio.client.feature.entry.domain.NightlyListing
import org.mercurio.client.feature.entry.domain.NightlyRelease
import org.mercurio.client.feature.entry.domain.NightlySigningMetadata
import org.mercurio.client.feature.entry.domain.NightlySigningMetadataResult
import org.mercurio.client.feature.entry.domain.NightlyUpdateFeed
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One GitHub API request: the absolute URL the feed already built, and the Accept media type the
 * answer must carry. Release metadata is requested through the API asset endpoint, so it is read
 * as raw text and never follows a download redirect.
 */
class GitHubReleaseHttpRequest(
    val url: String,
    val accept: String = GITHUB_API_MEDIA_TYPE,
) {
    companion object {
        const val GITHUB_API_MEDIA_TYPE = "application/vnd.github+json"
        const val GITHUB_RAW_MEDIA_TYPE = "application/vnd.github.raw"
    }
}

/** The GitHub answer exceeded the update transport's bounded body budget. */
class GitHubResponseTooLargeException :
    IOException("GitHub response exceeded the update budget")

/** One GitHub API answer, with a body the transport bounded. */
class GitHubReleaseHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** The GitHub API boundary the Nightly feed drives; a test double replaces it. */
interface GitHubReleaseHttpTransport {
    suspend fun get(request: GitHubReleaseHttpRequest): GitHubReleaseHttpResponse
}

/** The transport the application uses outside tests, constructed without exposing OkHttp to a caller. */
fun defaultGitHubReleaseHttpTransport(): GitHubReleaseHttpTransport = OkHttpGitHubReleaseHttpTransport()

/**
 * The OkHttp implementation: one GET with the request's Accept media type, no redirect, a bounded
 * body, a hard timeout, and cancellation that cancels the call.
 */
class OkHttpGitHubReleaseHttpTransport(
    client: OkHttpClient = OkHttpClient(),
) : GitHubReleaseHttpTransport {
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

    override suspend fun get(request: GitHubReleaseHttpRequest): GitHubReleaseHttpResponse =
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
                        val answer =
                            response.use {
                                runCatching {
                                    GitHubReleaseHttpResponse(
                                        statusCode = it.code,
                                        body = boundedBody(it.body),
                                    )
                                }
                            }
                        answer.fold(
                            onSuccess = { value -> if (continuation.isActive) continuation.resume(value) },
                            onFailure = { error -> if (continuation.isActive) continuation.resumeWithException(error) },
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

    private fun boundedBody(body: ResponseBody): String {
        val source = body.source()
        val buffer = Buffer()
        val limit = MAX_RESPONSE_BYTES + 1L
        while (buffer.size < limit) {
            val read = source.read(buffer, limit - buffer.size)
            if (read == -1L) break
        }
        if (buffer.size > MAX_RESPONSE_BYTES) throw GitHubResponseTooLargeException()
        return buffer.readUtf8()
    }

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT = "Mercurio"
        const val CALL_TIMEOUT_SECONDS = 15L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 10L
        const val MAX_RESPONSE_BYTES = 1_000_000L
    }
}

/**
 * Reads the Nightly channel from the repository's GitHub Releases listing. A release is a candidate
 * only when it is published, flagged as a prerelease, tagged for the Nightly channel, and carries the
 * application APK, the signing metadata, and the checksum asset.
 */
class GitHubNightlyUpdateFeed(
    private val transport: GitHubReleaseHttpTransport = defaultGitHubReleaseHttpTransport(),
    private val repository: String = DEFAULT_REPOSITORY,
    private val apiBase: String = DEFAULT_API_BASE,
) : NightlyUpdateFeed {
    override suspend fun publishedNightlies(): NightlyListing =
        requestListing("$apiBase/repos/$repository/releases?per_page=$PAGE_SIZE") { response ->
            parseReleases(response.body)?.let(NightlyListing::Listed)
                ?: NightlyListing.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
        }

    override suspend fun signingMetadata(release: NightlyRelease): NightlySigningMetadataResult =
        if (!release.metadataAddress.startsWith(HTTPS_PREFIX)) {
            NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
        } else {
            requestMetadata(release.metadataAddress, GitHubReleaseHttpRequest.GITHUB_RAW_MEDIA_TYPE) { response ->
                parseMetadata(response.body)?.let(NightlySigningMetadataResult::Available)
                    ?: NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
            }
        }

    private suspend fun requestListing(
        url: String,
        transform: (GitHubReleaseHttpResponse) -> NightlyListing,
    ): NightlyListing =
        try {
            val response = transport.get(GitHubReleaseHttpRequest(url))
            response.failure()?.let(NightlyListing::Failed) ?: transform(response)
        } catch (error: IOException) {
            NightlyListing.Failed(error.toFeedFailure())
        } catch (_: IllegalArgumentException) {
            NightlyListing.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
        }

    private suspend fun requestMetadata(
        url: String,
        accept: String,
        transform: (GitHubReleaseHttpResponse) -> NightlySigningMetadataResult,
    ): NightlySigningMetadataResult =
        try {
            val response = transport.get(GitHubReleaseHttpRequest(url, accept))
            response.failure()?.let(NightlySigningMetadataResult::Failed) ?: transform(response)
        } catch (error: IOException) {
            NightlySigningMetadataResult.Failed(error.toFeedFailure())
        } catch (_: IllegalArgumentException) {
            NightlySigningMetadataResult.Failed(NightlyFeedFailure.MALFORMED_RESPONSE)
        }

    private fun parseReleases(body: String): List<NightlyRelease>? {
        val array = parseJson(body) as? JsonArray ?: return null
        return array.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            release.toRelease()
        }
    }

    private fun JsonObject.toRelease(): NightlyRelease? =
        string("tag_name")?.takeIf { value -> value.startsWith(NIGHTLY_TAG_PREFIX) }?.let { tag ->
            string("published_at")?.let(::parsePublishedAt)?.let { published ->
                (this["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.let { assets ->
                    val assetNames = assets.mapNotNull { asset -> asset.string("name") }.toSet()
                    val metadataAddress =
                        assets.firstOrNull { asset -> asset.string("name") == SIGNING_METADATA_ASSET }
                            ?.long("id")
                            ?.let { assetId -> "$apiBase/repos/$repository/releases/assets/$assetId" }
                    val eligible =
                        boolean("draft") == false &&
                            boolean("prerelease") == true &&
                            assetNames.containsAll(REQUIRED_ASSETS)
                    if (eligible && metadataAddress != null) {
                        NightlyRelease(tag = tag, publishedAt = published, metadataAddress = metadataAddress)
                    } else {
                        null
                    }
                }
            }
        }

    private fun parseMetadata(body: String): NightlySigningMetadata? {
        val document = parseJson(body) as? JsonObject ?: return null
        val sourceSha = document.string("source_sha")
        val versionCode = document.long("version_code")
        val versionName = document.string("version_name")
        val nativeAbis = (document["native_abis"] as? JsonArray)?.mapNotNull { it.jsonString() }
        val apkSha256 = document.string("apk_sha256")
        val certificateSha256 = document.string("certificate_sha256")
        return when {
            sourceSha == null || versionCode == null || versionName == null -> null
            nativeAbis == null || apkSha256 == null || certificateSha256 == null -> null
            else ->
                NightlySigningMetadata(
                    sourceSha = sourceSha,
                    versionCode = versionCode,
                    versionName = versionName,
                    nativeAbis = nativeAbis,
                    apkSha256 = apkSha256,
                    certificateSha256 = certificateSha256,
                )
        }
    }

    private fun parseJson(body: String): JsonElement? = runCatching { Json.parseToJsonElement(body) }.getOrNull()

    private fun GitHubReleaseHttpResponse.failure(): NightlyFeedFailure? =
        when (statusCode) {
            HTTP_OK -> null
            HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> NightlyFeedFailure.RATE_LIMITED
            else -> NightlyFeedFailure.SERVICE_UNAVAILABLE
        }

    private companion object {
        const val DEFAULT_REPOSITORY = "Gvetri/Mercurio"
        const val DEFAULT_API_BASE = "https://api.github.com"
        const val PAGE_SIZE = 30
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTPS_PREFIX = "https://"
        const val NIGHTLY_TAG_PREFIX = "nightly-"
        const val SIGNING_METADATA_ASSET = "signing-metadata.json"
        val REQUIRED_ASSETS = setOf("mercurio.apk", SIGNING_METADATA_ASSET, "SHA256SUMS")
    }
}

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

private fun JsonObject.string(name: String): String? = primitive(name)?.takeIf(JsonPrimitive::isString)?.content

private fun JsonObject.boolean(name: String): Boolean? = primitive(name)?.booleanValue()

private fun JsonObject.long(name: String): Long? = primitive(name)?.longValue()

private fun JsonPrimitive.booleanValue(): Boolean? = takeIf { !isString }?.content?.toBooleanStrictOrNull()

private fun JsonPrimitive.longValue(): Long? = takeIf { !isString }?.content?.toLongOrNull()

private fun JsonElement.jsonString(): String? = (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private fun parsePublishedAt(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (error: DateTimeParseException) {
        null
    }

private fun IOException.toFeedFailure(): NightlyFeedFailure =
    when (this) {
        is GitHubResponseTooLargeException -> NightlyFeedFailure.SERVICE_UNAVAILABLE
        is UnknownHostException -> NightlyFeedFailure.OFFLINE
        is InterruptedIOException -> NightlyFeedFailure.SERVICE_UNAVAILABLE
        else -> NightlyFeedFailure.OFFLINE
    }

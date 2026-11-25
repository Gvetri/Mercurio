package org.mercurio.client.feature.entry.wiring

import android.content.Context
import android.util.Base64
import org.mercurio.client.feature.entry.application.normalizeGatewayEndpoint
import org.mercurio.client.feature.entry.data.DefaultRunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.data.RunSubmissionUncertaintyStorage
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Persists response-loss markers without storing prompts, responses, or transcript data. */
class SharedPreferencesRunSubmissionUncertaintyStore(
    context: Context,
) : RunSubmissionUncertaintyStore by
    DefaultRunSubmissionUncertaintyStore(SharedPreferencesRunSubmissionUncertaintyStorage(context))

private class SharedPreferencesRunSubmissionUncertaintyStorage(
    context: Context,
) : RunSubmissionUncertaintyStorage {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? =
        preferences.getString(recordKey(key), null)?.let(::decode)

    override fun write(
        key: PendingRunSubmissionKey,
        snapshot: RunSubmissionUncertaintySnapshot,
    ) {
        check(preferences.edit().putString(recordKey(key), encode(snapshot)).commit()) {
            "Could not persist the Gateway Run submission uncertainty marker."
        }
    }

    override fun remove(key: PendingRunSubmissionKey) {
        check(preferences.edit().remove(recordKey(key)).commit()) {
            "Could not remove the Gateway Run submission uncertainty marker."
        }
    }

    override fun clearEndpoint(endpoint: String) {
        val prefix = "$RECORDS_KEY.${endpointNamespace(endpoint)}."
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) {
            "Could not remove the Gateway Run submission uncertainty markers."
        }
    }

    private fun encode(record: RunSubmissionUncertaintySnapshot): String =
        listOf(
            FORMAT_VERSION,
            encodePart(record.attemptId),
            if (record.settled) "1" else "0",
            if (record.requiresRunMatch) "1" else "0",
            record.boundRunId?.value.orEmpty().let(::encodePart),
            record.knownRunIds
                .map { it.value }
                .sorted()
                .joinToString(",") { value -> encodePart(value) },
        ).joinToString("|")

    private fun decode(value: String): RunSubmissionUncertaintySnapshot? {
        val parts = value.split('|', limit = 6)
        return when {
            parts.size == CURRENT_PART_COUNT && parts[0] == FORMAT_VERSION -> decodeCurrent(parts)
            parts.size == LEGACY_PART_COUNT && parts[0] == LEGACY_FORMAT_VERSION -> decodeLegacy(parts)
            else -> null
        }
    }

    private fun recordKey(key: PendingRunSubmissionKey): String =
        "$RECORDS_KEY.${endpointNamespace(key.endpoint)}.${encodePart(key.sessionId.value)}"

    private fun encodePart(value: String): String =
        Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun endpointNamespace(endpoint: String): String {
        val canonicalEndpoint = runCatching { normalizeGatewayEndpoint(endpoint) }.getOrDefault(endpoint.trim())
        return MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalEndpoint.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and BYTE_MASK) }
    }

    private companion object {
        const val PREFERENCES_NAME = "gateway_run_submission_uncertainty"
        const val RECORDS_KEY = "records"
        const val FORMAT_VERSION = "2"
        const val LEGACY_FORMAT_VERSION = "1"
        private const val CURRENT_PART_COUNT = 6
        private const val LEGACY_PART_COUNT = 5
        private const val BYTE_MASK = 0xFF
    }
}

private const val UNCERTAINTY_LEGACY_ATTEMPT_ID = "legacy"

private const val CURRENT_ATTEMPT_PART = 1

private const val CURRENT_SETTLED_PART = 2

private const val CURRENT_RUN_MATCH_PART = 3

private const val CURRENT_BOUND_RUN_PART = 4

private const val CURRENT_KNOWN_RUNS_PART = 5

private const val LEGACY_SETTLED_PART = 1

private const val LEGACY_RUN_MATCH_PART = 2

private const val LEGACY_BOUND_RUN_PART = 3

private const val LEGACY_KNOWN_RUNS_PART = 4

private fun decodeUncertaintyPart(value: String): String =
    Base64.decode(value, Base64.NO_WRAP).toString(StandardCharsets.UTF_8)

private fun decodeCurrent(parts: List<String>): RunSubmissionUncertaintySnapshot? =
    runCatching {
        RunSubmissionUncertaintySnapshot(
            attemptId = decodeUncertaintyPart(parts[CURRENT_ATTEMPT_PART]),
            knownRunIds = decodeRunIds(parts[CURRENT_KNOWN_RUNS_PART]),
            boundRunId = decodeBoundRunId(parts[CURRENT_BOUND_RUN_PART]),
            settled = parts[CURRENT_SETTLED_PART] == "1",
            requiresRunMatch = parts[CURRENT_RUN_MATCH_PART] == "1",
        )
    }.getOrNull()

private fun decodeLegacy(parts: List<String>): RunSubmissionUncertaintySnapshot? =
    runCatching {
        RunSubmissionUncertaintySnapshot(
            attemptId = UNCERTAINTY_LEGACY_ATTEMPT_ID,
            knownRunIds = decodeRunIds(parts[LEGACY_KNOWN_RUNS_PART]),
            boundRunId = decodeBoundRunId(parts[LEGACY_BOUND_RUN_PART]),
            settled = parts[LEGACY_SETTLED_PART] == "1",
            requiresRunMatch = parts[LEGACY_RUN_MATCH_PART] == "1",
        )
    }.getOrNull()

private fun decodeRunIds(value: String): Set<RunId> =
    value
        .takeUnless(String::isEmpty)
        ?.split(',')
        ?.map { encoded -> RunId(decodeUncertaintyPart(encoded)) }
        ?.filter { it.value.isNotBlank() }
        ?.toSet()
        .orEmpty()

private fun decodeBoundRunId(value: String): RunId? =
    value.takeUnless(String::isEmpty)?.let { RunId(decodeUncertaintyPart(it)) }

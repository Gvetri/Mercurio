package org.hermesnative.client.feature.entry.wiring

import android.content.Context
import android.util.Base64
import org.hermesnative.client.feature.entry.application.normalizeGatewayEndpoint
import org.hermesnative.client.feature.entry.domain.SessionDraftKey
import org.hermesnative.client.feature.entry.domain.SessionDraftStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Persists the composer draft text for each Session so a failed submission or an app
 * restart can restore what the user typed. Only that text is stored, namespaced by the
 * normalized Gateway endpoint so drafts never leak across endpoints.
 */
class SharedPreferencesSessionDraftStore(
    context: Context,
) : SessionDraftStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(key: SessionDraftKey): String? = preferences.getString(recordKey(key), null)

    override fun write(
        key: SessionDraftKey,
        text: String,
    ) {
        preferences.edit().putString(recordKey(key), text).apply()
    }

    override fun remove(key: SessionDraftKey) {
        check(preferences.edit().remove(recordKey(key)).commit()) {
            "Could not remove the persisted Session composer draft."
        }
    }

    override fun clearEndpoint(endpoint: String) {
        val prefix = "$RECORDS_KEY.${endpointNamespace(endpoint)}."
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) {
            "Could not remove the persisted Session composer drafts."
        }
    }

    private fun recordKey(key: SessionDraftKey): String =
        "$RECORDS_KEY.${endpointNamespace(key.endpoint)}.${encodePart(key.sessionId.value)}"

    private fun encodePart(value: String): String {
        return Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    private fun endpointNamespace(endpoint: String): String {
        val canonicalEndpoint = runCatching { normalizeGatewayEndpoint(endpoint) }.getOrDefault(endpoint.trim())
        return MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalEndpoint.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
    }

    private companion object {
        const val PREFERENCES_NAME = "gateway_session_drafts"
        const val RECORDS_KEY = "drafts"
        const val BYTE_MASK = 0xFF
    }
}

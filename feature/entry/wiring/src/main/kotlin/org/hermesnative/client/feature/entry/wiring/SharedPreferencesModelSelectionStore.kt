package org.hermesnative.client.feature.entry.wiring

import android.content.Context
import android.util.Base64
import org.hermesnative.client.feature.entry.application.normalizeGatewayEndpoint
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.hermesnative.client.feature.entry.domain.ModelSelectionStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Persists the per-endpoint Run model override, favorite model IDs, and recently used
 * model IDs in the same SharedPreferences tier as the composer drafts. Records are
 * namespaced by the normalized Gateway endpoint so selections never leak across
 * endpoints; a cleared override returns submissions to the server default.
 */
class SharedPreferencesModelSelectionStore(
    context: Context,
) : ModelSelectionStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(endpoint: String): ModelSelection? {
        val model = preferences.getString(ModelSelectionKeys.modelKey(endpoint), null) ?: return null
        return ModelSelection(
            model = model,
            effort = preferences.getString(ModelSelectionKeys.effortKey(endpoint), null),
        )
    }

    override fun write(
        endpoint: String,
        selection: ModelSelection,
    ) {
        val editor = preferences.edit().putString(ModelSelectionKeys.modelKey(endpoint), selection.model)
        val effort = selection.effort
        if (effort == null) {
            editor.remove(ModelSelectionKeys.effortKey(endpoint))
        } else {
            editor.putString(ModelSelectionKeys.effortKey(endpoint), effort)
        }
        editor.apply()
    }

    override fun clear(endpoint: String) {
        removePrefixed(ModelSelectionKeys.selectionNamespacePrefix(endpoint))
    }

    override fun favorites(endpoint: String): Set<String> =
        preferences.getStringSet(ModelSelectionKeys.favoritesKey(endpoint), emptySet())?.toSet().orEmpty()

    override fun setFavorite(
        endpoint: String,
        modelId: String,
        favorite: Boolean,
    ) {
        val updated =
            favorites(endpoint).toMutableSet().apply {
                if (favorite) add(modelId) else remove(modelId)
            }
        preferences.edit().putStringSet(ModelSelectionKeys.favoritesKey(endpoint), updated).apply()
    }

    override fun recents(endpoint: String): List<String> =
        preferences
            .getString(ModelSelectionKeys.recentsKey(endpoint), null)
            ?.split(ModelSelectionKeys.RECENT_SEPARATOR)
            ?.filter(String::isNotEmpty)
            ?.map(ModelSelectionKeys::decodePart)
            .orEmpty()

    override fun recordUse(
        endpoint: String,
        modelId: String,
    ) {
        val updated =
            (listOf(modelId) + recents(endpoint).filterNot { it == modelId })
                .take(ModelSelectionKeys.MAX_RECENT_MODELS)
        preferences
            .edit()
            .putString(
                ModelSelectionKeys.recentsKey(endpoint),
                updated.joinToString(
                    ModelSelectionKeys.RECENT_SEPARATOR,
                    transform = ModelSelectionKeys::encodePart,
                ),
            )
            .apply()
    }

    override fun clearEndpoint(endpoint: String) {
        ModelSelectionKeys.endpointPrefixes(endpoint).forEach(::removePrefixed)
    }

    private fun removePrefixed(prefix: String) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        check(editor.commit()) {
            "Could not remove the persisted model selection records."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "gateway_model_selection"
    }
}

private object ModelSelectionKeys {
    const val MAX_RECENT_MODELS = 10
    const val RECENT_SEPARATOR = "\n"

    private const val SELECTION_RECORDS = "selection"
    private const val FAVORITES_RECORDS = "favorites"
    private const val RECENTS_RECORDS = "recents"
    private const val MODEL_RECORD = "model"
    private const val EFFORT_RECORD = "effort"
    private const val IDS_RECORD = "ids"
    private const val BYTE_MASK = 0xFF

    fun selectionNamespacePrefix(endpoint: String) = "$SELECTION_RECORDS.${endpointNamespace(endpoint)}."

    fun modelKey(endpoint: String) = "${selectionNamespacePrefix(endpoint)}$MODEL_RECORD"

    fun effortKey(endpoint: String) = "${selectionNamespacePrefix(endpoint)}$EFFORT_RECORD"

    fun favoritesKey(endpoint: String) = "$FAVORITES_RECORDS.${endpointNamespace(endpoint)}.$IDS_RECORD"

    fun recentsKey(endpoint: String) = "$RECENTS_RECORDS.${endpointNamespace(endpoint)}.$IDS_RECORD"

    fun endpointPrefixes(endpoint: String): List<String> {
        val namespace = endpointNamespace(endpoint)
        return listOf(
            "$SELECTION_RECORDS.$namespace.",
            "$FAVORITES_RECORDS.$namespace.",
            "$RECENTS_RECORDS.$namespace.",
        )
    }

    fun encodePart(value: String): String {
        return Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodePart(value: String): String = String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8)

    private fun endpointNamespace(endpoint: String): String {
        val canonicalEndpoint = runCatching { normalizeGatewayEndpoint(endpoint) }.getOrDefault(endpoint.trim())
        return MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalEndpoint.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
    }
}

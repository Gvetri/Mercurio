package org.hermesnative.client.feature.entry.presentation

import org.hermesnative.client.feature.entry.domain.ModelCatalog
import org.hermesnative.client.feature.entry.domain.ModelCatalogEntry
import org.hermesnative.client.feature.entry.domain.ModelSelection

/**
 * Presentation state of the composer Run model picker. The catalog vocabulary is
 * server-owned; this type only arranges what the catalog, the selection store, and the
 * discovered capability row say.
 *
 * The fail-closed ladder decides what renders: the row absent means no control and a
 * single truthful passive line; the row present but the catalog loading or unavailable
 * means no control either — never a selectable-but-dead affordance.
 */
data class ModelPickerUiState(
    val capabilityKnown: Boolean = false,
    val capabilityAdvertised: Boolean = false,
    val isFetchingCatalog: Boolean = false,
    val catalogFetchFailed: Boolean = false,
    val catalog: ModelCatalog? = null,
    val selection: ModelSelection? = null,
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val isPickerOpen: Boolean = false,
) {
    /** The selectable control exists only with an advertised row and a loaded catalog. */
    val showsSelectionControl: Boolean
        get() = capabilityAdvertised && catalog != null && !isFetchingCatalog

    /** The passive absent-state line renders only once the capability is known to be absent. */
    val showsUnavailableCopy: Boolean
        get() = capabilityKnown && !capabilityAdvertised

    /** One catalog attempt per armed picker state: fetch only while advertised with nothing loaded or decided. */
    val catalogFetchNeeded: Boolean
        get() = capabilityAdvertised && catalog == null && !isFetchingCatalog && !catalogFetchFailed

    private val selectedEntry: ModelCatalogEntry?
        get() = catalog?.entries?.firstOrNull { it.id == selection?.model }

    /** The currently-effective model as UI text: the selected display name or the baseline. */
    val controlLabel: String
        get() =
            when {
                selection == null -> SERVER_DEFAULT_LABEL
                else -> selectedEntry?.displayName ?: selection.model
            }

    /** The effort values offered for the selected entry; empty unless the entry advertises them. */
    val selectedEfforts: List<String>
        get() = selectedEntry?.effort?.supported.orEmpty()

    /** The server default preselected until the user's override names a value. */
    val effectiveEffort: String?
        get() = selectedEntry?.let { selection?.effort ?: it.effort?.default }

    /** The favorite toggle state of the selected entry id, used by the picker rows. */
    fun isFavorite(modelId: String): Boolean = modelId in favorites
}

internal const val SERVER_DEFAULT_LABEL = "Server default"

internal const val MODEL_CATALOG_UNAVAILABLE_COPY = "This Gateway does not offer model selection."

/**
 * Picker order: favorites first in catalog order, then recents most-recent-first, then
 * the rest in catalog order. Favorites or recents naming an unknown model are dropped.
 */
internal fun orderedModelEntries(
    catalog: ModelCatalog,
    favorites: Set<String>,
    recents: List<String>,
): List<ModelCatalogEntry> {
    val byId = catalog.entries.associateBy { it.id }
    val favoriteGroup = catalog.entries.filter { it.id in favorites }
    val knownIds = favoriteGroup.mapTo(mutableSetOf()) { it.id }
    val recentGroup = recents.mapNotNull(byId::get).filterNot { it.id in knownIds }
    val recentIds = recentGroup.mapTo(mutableSetOf()) { it.id }
    val rest = catalog.entries.filterNot { it.id in knownIds || it.id in recentIds }
    return favoriteGroup + recentGroup + rest
}

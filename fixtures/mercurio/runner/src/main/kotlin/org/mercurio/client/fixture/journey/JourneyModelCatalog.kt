package org.mercurio.client.fixture.journey

/**
 * The additive model catalog served at `GET /api/models` for journeys that script the
 * advertised catalog flow. The shape follows the pinned `contracts/models` fixture: a
 * nullable `default_model` and non-empty entries; an entry without efforts is selectable
 * with no effort control.
 */
data class JourneyModelCatalog(
    val defaultModel: String?,
    val entries: List<JourneyModelCatalogEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "A journey model catalog must contain at least one model entry." }
    }
}

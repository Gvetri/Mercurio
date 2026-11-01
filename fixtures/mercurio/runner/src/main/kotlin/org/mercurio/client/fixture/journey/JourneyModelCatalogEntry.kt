package org.mercurio.client.fixture.journey

data class JourneyModelCatalogEntry(
    val id: String,
    val displayName: String,
    val provider: String?,
    val supportedEfforts: List<String> = emptyList(),
    val defaultEffort: String? = null,
) {
    init {
        require(id.isNotBlank()) { "A journey model catalog entry must carry a non-blank id." }
        require(displayName.isNotBlank()) { "A journey model catalog entry must carry a non-blank display name." }
        require(supportedEfforts.isNotEmpty() || defaultEffort == null) {
            "A model entry without efforts cannot declare a default effort."
        }
        require(supportedEfforts.none(String::isBlank)) { "Journey model effort vocabulary values must not be blank." }
        require(defaultEffort == null || defaultEffort in supportedEfforts) {
            "A model entry default effort must be one of its supported efforts."
        }
    }
}

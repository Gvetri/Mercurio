package org.mercurio.client.fixture.journey

/** A deliberately mismatched `run_models` capability row: exact-method/path gates decide detection. */
data class JourneyModelCatalogRow(
    val method: String,
    val path: String,
) {
    init {
        require(method.isNotBlank()) { "A journey model catalog row override must carry a non-blank method." }
        require(path.isNotBlank()) { "A journey model catalog row override must carry a non-blank path." }
    }
}

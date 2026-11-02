package org.mercurio.client.feature.entry.domain

/**
 * One selectable model. [id] is the exact value the client sends as the Run-creation
 * `model` key; [displayName] is UI text and [provider] is informational only, never
 * credential material. A null [effort] means the server declares no effort control for
 * this model.
 */
data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val provider: String?,
    val effort: ModelEffortOptions?,
)

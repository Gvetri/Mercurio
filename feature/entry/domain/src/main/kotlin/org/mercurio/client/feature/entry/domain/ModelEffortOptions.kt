package org.mercurio.client.feature.entry.domain

/**
 * The effort control a catalog entry may carry. [supported] is a non-empty, ordered
 * list of server-owned values; [default] is a member of [supported] or null.
 */
data class ModelEffortOptions(
    val supported: List<String>,
    val default: String?,
)

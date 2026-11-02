package org.mercurio.client.feature.entry.domain

/**
 * A per-endpoint Run model override. The combined model-and-effort control keeps the
 * invariant that an effort is only ever carried with its model: a selection without a
 * model cannot be constructed, and no selection means the Server default is used with
 * no Run-creation selection keys.
 */
data class ModelSelection(
    val model: String,
    val effort: String? = null,
)

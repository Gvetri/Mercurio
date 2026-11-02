package org.mercurio.client.feature.entry.domain

/**
 * Run creation request over the pinned `POST /v1/runs` contract.
 *
 * `model` and `effort` are per-Run selections from the model catalog; an unset
 * selection is omitted from the request body and never sent as null.
 */
data class RunCreateRequest(
    val sessionId: SessionId,
    val input: String,
    val model: String? = null,
    val effort: String? = null,
)

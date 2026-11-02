package org.mercurio.client.feature.entry.domain

/**
 * Session list request over the pinned `GET /api/sessions` contract.
 *
 * The pinned Gateway pages with `limit` and `offset` and reports `has_more`
 * (projected to [SessionPage.nextOffset]); it does not accept a cursor token.
 */
data class SessionListRequest(
    val limit: Int = 20,
    val offset: Int = 0,
)

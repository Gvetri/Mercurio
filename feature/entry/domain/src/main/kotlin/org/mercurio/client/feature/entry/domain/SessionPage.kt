package org.mercurio.client.feature.entry.domain

data class SessionPage(
    val sessions: List<Session>,
    val nextOffset: Int?,
)

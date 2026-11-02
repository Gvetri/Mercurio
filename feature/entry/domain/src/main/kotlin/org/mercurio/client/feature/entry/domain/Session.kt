package org.mercurio.client.feature.entry.domain

import java.time.Instant

data class Session(
    val id: SessionId,
    val title: String?,
    val preview: String?,
    val pinned: Boolean,
    val lastActive: Instant? = null,
)

package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionId
import java.time.Instant

data class SessionItemUiState(
    val id: SessionId,
    val title: String,
    val preview: String?,
    val pinned: Boolean,
    val lastActive: RelativeLastActive = RelativeLastActive.Unknown,
    val isPendingTitle: Boolean = false,
)

internal fun Session.toSessionItemUiState(now: Instant = Instant.now()): SessionItemUiState {
    val normalizedTitle = title?.trim().orEmpty()
    val pendingTitle = normalizedTitle.isEmpty()
    return SessionItemUiState(
        id = id,
        title = normalizedTitle,
        preview = preview?.takeIf(String::isNotBlank),
        pinned = pinned,
        lastActive = relativeLastActive(lastActive, now),
        isPendingTitle = pendingTitle,
    )
}

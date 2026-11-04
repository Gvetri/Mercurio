package org.mercurio.client.feature.entry.presentation

import android.net.Uri

internal const val MERCURIO_SCHEME = "mercurio"
internal const val SESSION_LINK_HOST = "session"
internal const val COMPOSE_LINK_HOST = "compose"

/**
 * An inbound `mercurio://` link. Only the two approved actions exist: opening a
 * Session and opening the composer. [Session] carries a null id when the link
 * names the action but no usable Session id, so the caller can report the
 * unknown-link message instead of guessing a Session.
 */
internal sealed interface EntryLink {
    data class Session(
        val id: String?,
    ) : EntryLink

    data object Compose : EntryLink
}

/** The truthful message an inbound link or a shared payload reports when it cannot be applied. */
enum class InboundLinkNoticeCategory(
    val messageRes: Int,
) {
    SESSION_LINK_NEEDS_CONNECTION(R.string.inbound_link_session_needs_connection),
    COMPOSE_LINK_NEEDS_CONNECTION(R.string.inbound_link_compose_needs_connection),
    SESSION_LINK_UNKNOWN(R.string.inbound_link_session_unknown),
    COMPOSE_LINK_NEEDS_SESSION(R.string.inbound_link_compose_needs_session),
    SHARE_NEEDS_CONNECTION(R.string.shared_text_needs_connection),
    SHARE_UNSUPPORTED(R.string.shared_text_unsupported),
    SHARE_PICK_SESSION(R.string.shared_text_pick_session),
}

/**
 * Parses a raw URI into one of the two canonical actions. A foreign scheme or
 * a foreign host is not a Mercurio link and returns null. A `session` link
 * with a missing, blank, or multi-segment id returns [EntryLink.Session] with a
 * null id, so the caller reports the unknown-link message.
 */
internal fun parseEntryLink(rawUri: String?): EntryLink? {
    val uri = rawUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
    val scheme = uri?.scheme
    val host = uri?.host?.lowercase()
    val segments =
        uri?.let { parsed -> runCatching { parsed.pathSegments.orEmpty() }.getOrDefault(emptyList()) }
            .orEmpty()
            .filter(String::isNotBlank)
    return when {
        scheme == null || !scheme.equals(MERCURIO_SCHEME, ignoreCase = true) || host == null -> null
        host == SESSION_LINK_HOST ->
            EntryLink.Session(
                segments.singleOrNull()?.takeIf(String::isNotBlank),
            )
        host == COMPOSE_LINK_HOST -> EntryLink.Compose.takeIf { segments.isEmpty() }
        else -> null
    }
}

internal fun EntryLink.connectionNotice(): InboundLinkNoticeCategory =
    when (this) {
        is EntryLink.Session -> InboundLinkNoticeCategory.SESSION_LINK_NEEDS_CONNECTION
        EntryLink.Compose -> InboundLinkNoticeCategory.COMPOSE_LINK_NEEDS_CONNECTION
    }

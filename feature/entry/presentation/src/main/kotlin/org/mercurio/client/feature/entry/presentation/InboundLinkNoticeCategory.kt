package org.mercurio.client.feature.entry.presentation

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

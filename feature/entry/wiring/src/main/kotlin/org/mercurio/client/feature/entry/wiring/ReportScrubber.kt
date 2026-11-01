package org.mercurio.client.feature.entry.wiring

import io.sentry.SentryEvent

/** Removes everything an event could carry beyond the closed error-report contract. */
internal class ReportScrubber(
    private val forbiddenTagMarkers: Set<String> = FORBIDDEN_TAG_MARKERS,
) {
    /** Returns the same event stripped of volatile context, or null when it is already absent. */
    fun scrub(event: SentryEvent?): SentryEvent? {
        event ?: return null
        event.breadcrumbs = null
        event.user = null
        event.request = null
        event.extras = null
        event.serverName = null
        event.tags = event.tags?.filterKeys(::carriesNoForbiddenMarker)
        return event
    }

    private fun carriesNoForbiddenMarker(tag: String): Boolean =
        forbiddenTagMarkers.none { marker -> tag.contains(marker, ignoreCase = true) }
}

private val FORBIDDEN_TAG_MARKERS =
    setOf(
        "session",
        "draft",
        "credential",
        "token",
        "password",
        "authorization",
        "cookie",
        "gateway",
        "host",
        "dsn",
    )

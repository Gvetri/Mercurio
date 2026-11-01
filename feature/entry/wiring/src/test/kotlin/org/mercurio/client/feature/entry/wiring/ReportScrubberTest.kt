package org.mercurio.client.feature.entry.wiring

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportScrubberTest {
    private val scrubber = ReportScrubber()

    @Test
    fun strips_breadcrumbs_user_request_extras_and_server_name() {
        val event =
            SentryEvent().apply {
                breadcrumbs = listOf(Breadcrumb("draft text typed into the composer"))
                user = User()
                request = Request()
                extras = mapOf("session" to "conversation content")
                serverName = "gateway.internal.example"
            }

        val scrubbed = scrubber.scrub(event)

        assertNull(scrubbed?.breadcrumbs)
        assertNull(scrubbed?.user)
        assertNull(scrubbed?.request)
        assertNull(scrubbed?.extras)
        assertNull(scrubbed?.serverName)
    }

    @Test
    fun keeps_tags_that_carry_no_forbidden_marker() {
        val event =
            SentryEvent().apply {
                tags =
                    linkedMapOf(
                        "release" to "0.1.0",
                        "environment" to "debug",
                        "gateway.host" to "gateway.internal.example",
                        "session.draft" to "half a sentence",
                        "bearer_token" to "synthetic-token",
                        "credential" to "synthetic-credential",
                    )
            }

        val scrubbed = scrubber.scrub(event)

        assertEquals(
            linkedMapOf(
                "release" to "0.1.0",
                "environment" to "debug",
            ),
            scrubbed?.tags,
        )
    }

    @Test
    fun a_missing_event_stays_missing() {
        assertNull(scrubber.scrub(null))
    }
}

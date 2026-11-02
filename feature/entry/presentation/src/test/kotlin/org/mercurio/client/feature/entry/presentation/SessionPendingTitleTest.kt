package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionId
import java.time.Instant

/**
 * A Session the Gateway has not titled yet carries no title at all: the placeholder the
 * design prints is copy the renderer supplies, so searching cannot match it and a rename
 * draft cannot start from it.
 */
class SessionPendingTitleTest {
    @Test
    fun an_untitled_session_projects_to_an_empty_title_and_the_pending_marker() {
        val item = untitledSession().toSessionItemUiState(NOW)

        assertEquals("", item.title)
        assertTrue(item.isPendingTitle)
    }

    @Test
    fun search_matches_server_provided_titles_only_and_never_the_pending_placeholder() {
        val titled = Session(SessionId("titled"), "New release notes", null, false)
        val list =
            SessionListUiState(
                sessions =
                    listOf(
                        untitledSession().toSessionItemUiState(NOW),
                        titled.toSessionItemUiState(NOW),
                    ),
                searchQuery = "new",
            )

        assertEquals(listOf("titled"), list.visibleSessions.map { session -> session.id.value })
    }

    private fun untitledSession(): Session = Session(SessionId("untitled"), null, "Draft preview", false)

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-02T08:00:00Z")
    }
}

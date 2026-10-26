package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.concurrent.TimeUnit

private const val TEST_TIMEOUT_MILLIS = 5_000L

class MessageSubmissionInFlightDraftTest {
    @Test
    fun a_noop_composer_edit_while_the_submission_is_in_flight_still_clears_the_confirmed_draft() {
        val session = session("session-1")
        val run = Run(RunId("run-1"), session.id, "starting")
        val gateway =
            ObservationScriptedGateway(session).apply {
                blockCreate = true
                enqueueRun(run)
            }
        val holder = observationHolder(gateway, Dispatchers.Default)

        try {
            openObservedSession(holder, gateway, session.id)
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            assertTrue(gateway.createStarted.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
            gateway.createRelease.countDown()
            awaitObservationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.latestRun?.id == run.id && !opened.isSending
            }

            val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
            assertEquals("", opened.composerText)
        } finally {
            holder.close()
        }
    }

    private fun session(id: String): Session =
        Session(
            id = SessionId(id),
            title = "Session $id",
            preview = "Preview",
            pinned = false,
        )
}

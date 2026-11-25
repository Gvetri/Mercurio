package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionId

class MessageSubmissionInFlightDraftTest {
    @Test
    fun a_noop_composer_edit_while_the_submission_is_in_flight_still_clears_the_confirmed_draft() =
        runTest {
            val session = session("session-1")
            val run = Run(RunId("run-1"), session.id, "starting")
            val gateway =
                ObservationScriptedGateway(session).apply {
                    enqueueRun(run)
                    blockCreate = true
                }
            val holder = observationHolder(gateway)

            try {
                openObservedSession(holder, gateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                gateway.createStarted.await()
                assertTrue(requireNotNull(holder.uiState.value.sessionList?.openedSession).isSending)

                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run this"))
                gateway.createRelease.complete(Unit)
                awaitObservationState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened?.latestRun?.id == run.id && !opened.isSending
                }

                val opened = requireNotNull(requireNotNull(holder.uiState.value.sessionList).openedSession)
                assertEquals("", opened.composerText)
                assertEquals(1, gateway.createRunCount)
            } finally {
                gateway.createRelease.complete(Unit)
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

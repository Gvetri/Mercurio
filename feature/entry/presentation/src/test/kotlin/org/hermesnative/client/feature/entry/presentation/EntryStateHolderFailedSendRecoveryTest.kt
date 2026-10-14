package org.hermesnative.client.feature.entry.presentation

import org.hermesnative.client.feature.entry.data.DefaultRunSubmissionUncertaintyStore
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunEvent
import org.hermesnative.client.feature.entry.domain.RunEventType
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.SessionDraftKey
import org.hermesnative.client.feature.entry.domain.SessionDraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic in-memory double for the persisted composer draft boundary. */
internal class InMemorySessionDraftStore : SessionDraftStore {
    private val drafts = mutableMapOf<SessionDraftKey, String>()

    override fun read(key: SessionDraftKey): String? = drafts[key]

    override fun write(
        key: SessionDraftKey,
        text: String,
    ) {
        drafts[key] = text
    }

    override fun remove(key: SessionDraftKey) {
        drafts.remove(key)
    }

    override fun clearEndpoint(endpoint: String) {
        drafts.keys.filter { it.endpoint == endpoint }.toList().forEach(drafts::remove)
    }
}

private const val FAILED_SEND_DRAFT = "Recover this draft"

class EntryStateHolderFailedSendRecoveryTest {
    @Test
    fun a_failed_send_recovers_when_the_gateway_is_reachable_again() {
        val session = reconciliationSession()
        val recoveryRun =
            Run(RunId("recovery-run"), session.id, "running")
        val gateway =
            ReconciliationFakeGateway(session).apply {
                failRunCreation = true
            }
        val holder =
            reconciliationHolder(
                gateway,
                settings =
                    ReconciliationHolderSettings(
                        uncertaintyStore = DefaultRunSubmissionUncertaintyStore(ReconciliationUncertaintyStorage()),
                    ),
            )

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged(FAILED_SEND_DRAFT))
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == true &&
                    opened.sendErrorCategory == MessageSendErrorCategory.GATEWAY_REQUEST_FAILED &&
                    opened.composerText == FAILED_SEND_DRAFT
            }
            assertEquals(1, gateway.runRequests.size)

            arrangeGatewayRecovery(gateway, recoveryRun)
            holder.onEvent(EntryUiEvent.SendMessageClicked)

            awaitReconciliationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == false &&
                    opened.sendErrorCategory == null &&
                    opened.composerText.isEmpty()
            }
            assertEquals(
                listOf(session.id to FAILED_SEND_DRAFT, session.id to FAILED_SEND_DRAFT),
                gateway.runRequests,
            )
            assertEquals(listOf(recoveryRun.id), gateway.observedRunIds)
        } finally {
            holder.close()
            joinHolder(holder)
        }
    }

    @Test
    fun a_failed_send_draft_survives_an_app_restart() {
        val session = reconciliationSession()
        val recoveryRun = Run(RunId("recovery-run-restart"), session.id, "running")
        val gateway = ReconciliationFakeGateway(session).apply { failRunCreation = true }
        val uncertaintyStorage = ReconciliationUncertaintyStorage()
        val draftStore = InMemorySessionDraftStore()
        val firstHolder =
            reconciliationHolder(
                gateway,
                settings =
                    ReconciliationHolderSettings(
                        uncertaintyStore = DefaultRunSubmissionUncertaintyStore(uncertaintyStorage),
                        sessionDraftStore = draftStore,
                    ),
            )

        try {
            openReconciledSession(firstHolder, gateway)
            firstHolder.onEvent(EntryUiEvent.ComposerTextChanged(FAILED_SEND_DRAFT))
            firstHolder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitReconciliationState(firstHolder) {
                it.sessionList?.openedSession?.hasUnresolvedSubmission == true
            }
            firstHolder.close()
        } finally {
            firstHolder.close()
        }

        arrangeGatewayRecovery(gateway, recoveryRun, statusMatches = 2)
        val secondHolder = restartedHolder(gateway, uncertaintyStorage, draftStore)

        try {
            connectReconciliationGateway(secondHolder, gateway)
            secondHolder.onEvent(EntryUiEvent.SessionClicked(session.id))
            awaitReconciliationState(secondHolder) {
                val opened = it.sessionList?.openedSession
                opened?.session?.id == session.id &&
                    opened.composerText == FAILED_SEND_DRAFT
            }
            awaitReconciliationState(secondHolder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == false
            }

            secondHolder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitReconciliationState(secondHolder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == false &&
                    opened.composerText.isEmpty()
            }
            assertEquals(
                listOf(session.id to FAILED_SEND_DRAFT, session.id to FAILED_SEND_DRAFT),
                gateway.runRequests,
            )
        } finally {
            secondHolder.close()
            joinHolder(secondHolder)
        }
    }

    @Test
    fun discarding_the_unresolved_record_restores_the_composer_and_keeps_the_draft() {
        val session = reconciliationSession()
        val recoveryRun = Run(RunId("recovery-run-discard"), session.id, "running")
        val gateway = ReconciliationFakeGateway(session).apply { failRunCreation = true }
        val holder =
            reconciliationHolder(
                gateway,
                settings =
                    ReconciliationHolderSettings(
                        uncertaintyStore = DefaultRunSubmissionUncertaintyStore(ReconciliationUncertaintyStorage()),
                    ),
            )

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged(FAILED_SEND_DRAFT))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.hasUnresolvedSubmission == true
            }

            holder.onEvent(EntryUiEvent.DiscardUnresolvedSendClicked)
            holder.onEvent(EntryUiEvent.ConfirmDiscardUnresolvedSendClicked)

            awaitReconciliationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == false &&
                    opened.sendErrorCategory == null &&
                    opened.composerText == FAILED_SEND_DRAFT
            }
            assertEquals(1, gateway.runRequests.size)

            arrangeGatewayRecovery(gateway, recoveryRun)
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitReconciliationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.hasUnresolvedSubmission == false && opened.composerText.isEmpty()
            }
            assertEquals(
                listOf(session.id to FAILED_SEND_DRAFT, session.id to FAILED_SEND_DRAFT),
                gateway.runRequests,
            )
        } finally {
            holder.close()
            joinHolder(holder)
        }
    }

    @Test
    fun cancel_keeps_the_unresolved_record_and_the_confirmation_closed() {
        val session = reconciliationSession()
        val gateway = ReconciliationFakeGateway(session).apply { failRunCreation = true }
        val holder =
            reconciliationHolder(
                gateway,
                settings =
                    ReconciliationHolderSettings(
                        uncertaintyStore = DefaultRunSubmissionUncertaintyStore(ReconciliationUncertaintyStorage()),
                    ),
            )

        try {
            openReconciledSession(holder, gateway)
            holder.onEvent(EntryUiEvent.ComposerTextChanged(FAILED_SEND_DRAFT))
            holder.onEvent(EntryUiEvent.SendMessageClicked)
            awaitReconciliationState(holder) {
                it.sessionList?.openedSession?.hasUnresolvedSubmission == true
            }

            holder.onEvent(EntryUiEvent.DiscardUnresolvedSendClicked)
            holder.onEvent(EntryUiEvent.CancelDiscardUnresolvedSendClicked)

            awaitReconciliationState(holder) {
                val opened = it.sessionList?.openedSession
                opened?.isDiscardUnresolvedSendConfirmationOpen == false
            }
            assertTrue(holder.uiState.value.sessionList?.openedSession?.hasUnresolvedSubmission == true)
        } finally {
            holder.close()
            joinHolder(holder)
        }
    }

    private fun restartedHolder(
        gateway: ReconciliationFakeGateway,
        uncertaintyStorage: ReconciliationUncertaintyStorage,
        draftStore: SessionDraftStore,
    ): EntryStateHolder =
        reconciliationHolder(
            gateway,
            settings =
                ReconciliationHolderSettings(
                    uncertaintyStore = DefaultRunSubmissionUncertaintyStore(uncertaintyStorage),
                    sessionDraftStore = draftStore,
                ),
        )

    private fun arrangeGatewayRecovery(
        gateway: ReconciliationFakeGateway,
        recoveryRun: Run,
        statusMatches: Int = 1,
    ) {
        gateway.failRunCreation = false
        gateway.runs.add(recoveryRun)
        repeat(statusMatches) { gateway.statuses.add(recoveryRun) }
        gateway.observation =
            ReconciliationScriptedObservation(
                listOf(
                    RunEvent(RunEventType.STARTED, recoveryRun.id, "starting", eventId = "started"),
                    RunEvent(RunEventType.SUCCEEDED, recoveryRun.id, "succeeded", eventId = "succeeded"),
                ),
            )
    }
}

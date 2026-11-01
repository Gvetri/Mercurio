package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SessionActionMenuIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun list_management_actions_target_the_correct_session() {
        val gateway = MenuGateway()
        val holder = showConnectedSession(gateway, opened = false)
        try {
            openMenu("First Session")
            assertTrue(gateway.mutations.isEmpty())
            choose("Rename")
            composeTestRule
                .onNodeWithContentDescription("Session title")
                .performTextReplacement("Requested title")
            assertEquals("First Session", firstSession(holder).title)
            gateway.confirmedRenameTitle = "Gateway title"
            chooseAction("Confirm rename")
            assertEquals("Gateway title", firstSession(holder).title)

            openMenu("Gateway title")
            choose("Pin")
            assertTrue(firstSession(holder).pinned)

            openMenu("Gateway title")
            choose("Unpin")
            assertFalse(firstSession(holder).pinned)

            openMenu("Gateway title")
            choose("Delete")
            composeTestRule.onNodeWithText("Delete “Gateway title”?").assertIsDisplayed()
            choose("Cancel")
            assertTrue(requireNotNull(holder.uiState.value.sessionList).sessions.any { it.id == FIRST })

            openMenu("Gateway title")
            choose("Delete")
            choose("Delete")

            assertEquals(listOf(SECOND), requireNotNull(holder.uiState.value.sessionList).sessions.map { it.id })
            assertNull(holder.uiState.value.sessionList?.openedSession)
            assertEquals(
                listOf(
                    SessionMutationAction.RENAME to FIRST,
                    SessionMutationAction.PIN to FIRST,
                    SessionMutationAction.UNPIN to FIRST,
                    SessionMutationAction.DELETE to FIRST,
                ),
                gateway.mutations,
            )
        } finally {
            holder.close()
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun two_pane_management_updates_the_open_conversation() {
        val gateway = MenuGateway()
        val holder = showConnectedSession(gateway, opened = true)
        try {
            composeTestRule
                .onNode(hasText("First Session") and hasClickAction())
                .performTouchInput { longClick() }
            choose("Rename")
            composeTestRule
                .onNodeWithContentDescription("Session title")
                .performTextReplacement("Requested title")
            gateway.confirmedRenameTitle = "Gateway title"
            chooseAction("Confirm rename")

            assertEquals("Gateway title", firstSession(holder).title)
            assertEquals("Gateway title", holder.uiState.value.sessionList?.openedSession?.session?.title)
            assertEquals(1, gateway.mutations.size)
        } finally {
            holder.close()
        }
    }

    @Test
    fun list_mutation_failure_preserves_state_until_the_row_retry_succeeds() {
        val gateway = MenuGateway()
        val holder = showConnectedSession(gateway, opened = false)
        try {
            gateway.failNextMutation = true
            openMenu("First Session")
            choose("Pin")
            assertFalse(firstSession(holder).pinned)
            composeTestRule
                .onNodeWithText(SessionMutationErrorCategory.GATEWAY_REQUEST_FAILED.safeMessage)
                .assertIsDisplayed()
            assertEquals(1, gateway.mutations.size)

            choose("Try again")
            assertTrue(firstSession(holder).pinned)
            assertEquals(2, gateway.mutations.size)
        } finally {
            holder.close()
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun list_refresh_and_pagination_update_the_delete_confirmation_metadata() {
        val gateway = MenuGateway()
        gateway.paginationEnabled = true
        val holder = showConnectedSession(gateway, opened = true)
        try {
            composeTestRule.onNodeWithText("Message").performTextReplacement("Keep this draft")
            composeTestRule.waitForIdle()
            assertEquals(listOf(FIRST, SECOND), holder.uiState.value.sessionList?.sessions?.map { it.id })

            gateway.changeServerMetadata("Server title", pinned = true)
            holder.onEvent(EntryUiEvent.RefreshSessionListClicked)
            composeTestRule.waitForIdle()

            composeTestRule
                .onNode(hasText("Server title") and hasClickAction())
                .performTouchInput { longClick() }
            choose("Unpin")
            openMenu("Server title")
            choose("Delete")

            composeTestRule.onNodeWithText("Delete “Server title”?").assertIsDisplayed()
            assertEquals("Keep this draft", holder.uiState.value.sessionList?.openedSession?.composerText)
            assertEquals(1, gateway.mutations.size)
        } finally {
            holder.close()
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun search_is_disabled_while_a_session_mutation_is_unresolved() {
        val gateway = MenuGateway()
        val holder = showConnectedSession(gateway, opened = false)
        try {
            gateway.failNextMutation = true
            openMenu("First Session")
            choose("Pin")
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithContentDescription("Search Sessions").assertIsNotEnabled()
            composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
        } finally {
            holder.close()
        }
    }

    private fun showConnectedSession(
        gateway: MenuGateway,
        opened: Boolean,
    ): EntryStateHolder {
        val holder =
            EntryStateHolder(
                initialState = EntryState(isGatewayConnectionConfigured = false),
                verifyGatewayConnection =
                    VerifyGatewayConnection(
                        object : GatewayConnectionRepository {
                            override fun load(): GatewayConnection? = null

                            override fun save(connection: GatewayConnection) = Unit
                        },
                    ) { _, _ -> GatewayCapabilities(PublicBetaGatewayCapabilityManifest.current.requiredEndpoints) },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                sessionGatewayFactory = { _, _ -> gateway },
            )
        holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
        holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example"))
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("synthetic-token"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        if (opened) holder.onEvent(EntryUiEvent.SessionClicked(FIRST))
        composeTestRule.setContent {
            val state by holder.uiState.collectAsState()
            HermesTheme {
                EntryScreen(state = state, onEvent = holder::onEvent)
            }
        }
        return holder
    }

    private fun firstSession(holder: EntryStateHolder): SessionItemUiState =
        requireNotNull(holder.uiState.value.sessionList).sessions.single { it.id == FIRST }

    private fun openMenu(title: String) {
        composeTestRule
            .onNode(hasText(title) and hasClickAction())
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
    }

    private fun choose(text: String) {
        composeTestRule.onNodeWithText(text).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    private fun chooseAction(description: String) {
        composeTestRule.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    private class MenuGateway : SessionGatewayPort {
        private val sessions =
            mutableListOf(
                Session(FIRST, "First Session", "Synthetic preview", false),
                Session(SECOND, "Second Session", "Synthetic preview", false),
            )
        val mutations = mutableListOf<Pair<SessionMutationAction, SessionId>>()
        var failNextMutation = false
        var confirmedRenameTitle: String? = null
        var historyMessages: List<GatewayHistoryMessage> = emptyList()
        var paginationEnabled = false
        var firstSessionOnSecondPage = false

        fun changeServerMetadata(
            title: String,
            pinned: Boolean,
        ) {
            val index = sessions.indexOfFirst { it.id == FIRST }
            sessions[index] = sessions[index].copy(title = title, pinned = pinned)
        }

        override fun listSessions(request: SessionListRequest): SessionPage =
            if (firstSessionOnSecondPage) {
                if (request.offset == 0) {
                    SessionPage(sessions.filter { it.id == SECOND }, 1)
                } else {
                    SessionPage(sessions.filter { it.id == FIRST }, null)
                }
            } else {
                SessionPage(sessions.toList(), if (paginationEnabled && request.offset == 0) 2 else null)
            }

        override fun createSession(title: String?): Session = error("Creation is not part of these journeys")

        override fun openSession(sessionId: SessionId): Session = sessions.single { it.id == sessionId }

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(
                sessionId,
                historyMessages,
            )

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session {
            recordMutation(SessionMutationAction.RENAME, sessionId)
            val index = sessions.indexOfFirst { it.id == sessionId }
            return sessions[index].copy(title = confirmedRenameTitle ?: title).also { sessions[index] = it }
        }

        override fun deleteSession(sessionId: SessionId) {
            recordMutation(SessionMutationAction.DELETE, sessionId)
            sessions.removeAll { it.id == sessionId }
        }

        override fun pinSession(sessionId: SessionId): SessionPinResult = changePin(sessionId, pinned = true)

        override fun unpinSession(sessionId: SessionId): SessionPinResult = changePin(sessionId, pinned = false)

        private fun changePin(
            sessionId: SessionId,
            pinned: Boolean,
        ): SessionPinResult {
            recordMutation(if (pinned) SessionMutationAction.PIN else SessionMutationAction.UNPIN, sessionId)
            val index = sessions.indexOfFirst { it.id == sessionId }
            sessions[index] = sessions[index].copy(pinned = pinned)
            return SessionPinResult(sessionId, pinned)
        }

        private fun recordMutation(
            action: SessionMutationAction,
            sessionId: SessionId,
        ) {
            mutations += action to sessionId
            if (failNextMutation) {
                failNextMutation = false
                throw GatewayException(GatewayErrorCategory.GATEWAY_REQUEST_FAILED)
            }
        }
    }

    private companion object {
        val FIRST = SessionId("first")
        val SECOND = SessionId("second")
    }
}

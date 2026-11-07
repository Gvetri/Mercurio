package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.data.InMemoryRunRecoveryRegistry
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class EntryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun launch_state_has_meaningful_semantics_and_usable_controls_at_increased_font_scale() {
        val stateHolder =
            EntryStateHolder(
                EntryState(isGatewayConnectionConfigured = false),
            )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                HermesTheme {
                    EntryScreen(
                        state = stateHolder.uiState.collectAsState().value,
                        onEvent = stateHolder::onEvent,
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText("Connect to a Hermes Gateway")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Add Gateway Connection")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(stateHolder.uiState.value.connectionSetupRequested)
        }
    }

    @Test
    fun connection_form_has_accessible_fields_and_requires_explicit_verification() {
        val events = mutableListOf<EntryUiEvent>()
        val state =
            EntryUiState(
                title = UiText.Resource(R.string.entry_title_verify_new),
                supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
                actionLabel = UiText.Resource(R.string.entry_action_verify),
                connectionSetupRequested = true,
                endpoint = "https://gateway.example",
                bearerCredential = "synthetic-token",
            )

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Gateway address", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Gateway credential", substring = true).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Ask your agent for a credential. It is sent only to your Gateway.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Save securely on this device").assertDoesNotExist()
        composeTestRule
            .onNodeWithText("Connect")
            .assertHasClickAction()
            .performClick()

        assertEquals(EntryUiEvent.VerifyGatewayConnectionClicked, events.single())
    }

    @Test
    fun secure_save_progress_and_storage_failure_render_the_approved_recovery() {
        val state =
            mutableStateOf(
                EntryUiState(
                    title = UiText.Resource(R.string.entry_title_verify_new),
                    supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
                    actionLabel = UiText.Resource(R.string.entry_action_verify),
                    connectionSetupRequested = true,
                    endpoint = "https://gateway.example/profile",
                    bearerCredential = "synthetic-token",
                    isSaving = true,
                ),
            )
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saving…").assertIsNotEnabled()

        composeTestRule.runOnIdle {
            state.value =
                state.value.copy(
                    isSaving = false,
                    errorCategory = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED,
                )
        }

        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The phone would not save the credential. It is not connected yet.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("gateway.example/profile", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("[ try again ]").assertIsEnabled().performClick()

        assertEquals(EntryUiEvent.TryAgainClicked, events.single())
    }

    @Test
    fun replacement_form_shows_a_fixed_endpoint_and_the_unchanged_connection_guarantee() {
        val state =
            mutableStateOf(
                EntryUiState(
                    title = UiText.Resource(R.string.entry_title_change_credential),
                    supportingText = UiText.Resource(R.string.entry_supporting_change_credential),
                    actionLabel = UiText.Resource(R.string.entry_action_verify_and_save),
                    connectionSetupRequested = true,
                    isChangingCredential = true,
                    isConnected = true,
                    endpoint = "https://gateway.example/profile",
                ),
            )
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state.value, onEvent = {})
            }
        }

        composeTestRule
            .onNodeWithText("https://gateway.example/profile")
            .assertIsDisplayed()
            .assert(hasSetTextAction().not())
        composeTestRule.onNodeWithText("Example: https://gateway.example").assertDoesNotExist()
        composeTestRule
            .onNodeWithText(
                "Change the credential. The address stays; the old connection keeps working " +
                    "until the new one is saved.",
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Verify and save").assertHasClickAction().assertIsNotEnabled()

        composeTestRule.runOnIdle {
            state.value =
                state.value.copy(
                    bearerCredential = "replacement-token",
                    errorCategory = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED,
                    isConnected = false,
                )
        }

        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("The phone would not save the credential. It is not connected yet.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("[ try again ]").assertIsEnabled()
    }

    @Test
    fun remove_confirmation_dialog_opens_over_the_reconnect_form_with_the_same_actions() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_verify_saved),
                            supportingText = UiText.Resource(R.string.entry_supporting_verify_saved),
                            actionLabel = UiText.Resource(R.string.entry_action_verify),
                            connectionSetupRequested = true,
                            isGatewayConnectionConfigured = true,
                            errorCategory = EntryErrorCategory.GATEWAY_REQUEST_FAILED,
                            endpoint = "https://gateway.example",
                            isRemoveConfirmationOpen = true,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Your Gateway").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove connection?").assertIsDisplayed()
        composeTestRule.onNodeWithText(removeBody()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(EntryUiEvent.CancelRemoveGatewayConnectionClicked, events.single())

        events.clear()
        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked, events.single())
    }

    @Test
    fun remove_confirmation_dialog_offers_cancel_and_remove_for_the_exact_connection() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connected),
                            supportingText = UiText.Value(""),
                            actionLabel = UiText.Value(""),
                            isConnected = true,
                            sessionList = SessionListUiState(),
                            isRemoveConfirmationOpen = true,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Remove connection?").assertIsDisplayed()
        composeTestRule.onNodeWithText(removeBody()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(EntryUiEvent.CancelRemoveGatewayConnectionClicked, events.single())

        events.clear()
        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked, events.single())
    }

    @Test
    fun loading_state_and_each_error_category_offer_clear_recovery() {
        val loadingState =
            EntryUiState(
                title = UiText.Resource(R.string.entry_title_verify_new),
                supportingText = UiText.Value("Checking the Gateway."),
                actionLabel = UiText.Resource(R.string.entry_action_verify),
                connectionSetupRequested = true,
                isVerifying = true,
            )
        val state = mutableStateOf(loadingState)
        val events = mutableListOf<EntryUiEvent>()

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = state.value,
                    onEvent = events::add,
                )
            }
        }
        composeTestRule.onNodeWithText("Checking the Gateway").assertIsDisplayed()

        val expectedCopy =
            mapOf(
                EntryErrorCategory.INVALID_ADDRESS to null,
                EntryErrorCategory.AUTHENTICATION_FAILED to copy(R.string.gateway_error_credential),
                EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE to copy(R.string.gateway_error_capability),
                EntryErrorCategory.SECURE_CONNECTION_FAILED to copy(R.string.gateway_error_certificate),
                EntryErrorCategory.GATEWAY_REQUEST_FAILED to copy(R.string.gateway_error_network),
                EntryErrorCategory.CREDENTIAL_STORAGE_FAILED to copy(R.string.gateway_error_storage),
            )
        expectedCopy.forEach { (category, expected) ->
            events.clear()
            composeTestRule.runOnIdle {
                state.value =
                    EntryUiState(
                        title = UiText.Resource(R.string.entry_title_verify_new),
                        supportingText = UiText.Value("Correct the details and try again."),
                        actionLabel = UiText.Resource(R.string.entry_action_verify),
                        connectionSetupRequested = true,
                        endpoint = "https://gateway.example",
                        bearerCredential = "synthetic-token",
                        errorCategory = category,
                    )
            }

            expected?.let { text -> composeTestRule.onNodeWithText(text).assertIsDisplayed() }
            when (category) {
                EntryErrorCategory.INVALID_ADDRESS,
                EntryErrorCategory.AUTHENTICATION_FAILED,
                EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
                -> Unit
                EntryErrorCategory.SECURE_CONNECTION_FAILED,
                EntryErrorCategory.GATEWAY_REQUEST_FAILED,
                EntryErrorCategory.CREDENTIAL_STORAGE_FAILED,
                -> {
                    composeTestRule.onNodeWithText("[ try again ]").performClick()
                    assertEquals(EntryUiEvent.TryAgainClicked, events.single())
                }
            }
        }
    }

    @Test
    fun connected_state_is_rendered_without_a_second_connection_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        EntryUiState(
                            title = UiText.Resource(R.string.entry_title_connected),
                            supportingText = UiText.Resource(R.string.entry_supporting_connected),
                            actionLabel = UiText.Resource(R.string.entry_action_connected),
                            connectionSetupRequested = true,
                            isConnected = true,
                            endpoint = "https://gateway.example",
                            gatewayFacts = UiText.Resource(R.string.gateway_facts_verified, listOf("09:41")),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Your Gateway").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://gateway.example").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Verified 09:41 · supports sessions, streaming and models")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("[ manage ]").performClick()
        composeTestRule.onNodeWithText("Change credential").performClick()
        assertEquals(listOf(EntryUiEvent.ChangeGatewayCredentialClicked), events)
    }

    @Test
    fun a_saved_connection_with_a_rejected_credential_keeps_the_form() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        gatewayFormState().copy(
                            isGatewayConnectionConfigured = true,
                            errorCategory = EntryErrorCategory.AUTHENTICATION_FAILED,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Connect to your Gateway").assertIsDisplayed()
        composeTestRule.onNodeWithText("[ try again ]").assertDoesNotExist()
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun the_bar_back_closes_the_setup_form() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = gatewayFormState(), onEvent = events::add)
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(EntryUiEvent.CloseGatewayConnectionSetupClicked, events.single())
    }

    @Test
    fun the_bar_back_cancels_a_credential_change() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = gatewayFormState().copy(isChangingCredential = true, isConnected = true),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(EntryUiEvent.CancelGatewayCredentialChangeClicked, events.single())
    }

    @Test
    fun the_bar_back_returns_a_saved_connection_to_the_session_list() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                GatewayConnectionScreen(
                    state =
                        gatewayFormState().copy(
                            isConnected = true,
                            sessionList = SessionListUiState(),
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(EntryUiEvent.ReturnToSessionListClicked, events.single())
    }

    @Test
    fun the_bar_back_leaves_a_saved_connection_that_cannot_reconnect() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        gatewayFormState().copy(
                            isGatewayConnectionConfigured = true,
                            errorCategory = EntryErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(EntryUiEvent.CloseGatewayConnectionSetupClicked, events.single())
    }

    @Test
    fun a_capability_failure_offers_the_address_action_and_focuses_the_field() {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        gatewayFormState().copy(
                            errorCategory = EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("[ change address ]").performClick()

        composeTestRule.onAllNodes(hasSetTextAction())[0].assertIsFocused()
    }

    @Test
    fun a_saved_connection_failure_offers_one_recovery_and_no_primary_action() {
        val events = mutableListOf<EntryUiEvent>()
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state =
                        gatewayFormState().copy(
                            isGatewayConnectionConfigured = true,
                            errorCategory = EntryErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                    onEvent = events::add,
                )
            }
        }

        composeTestRule.onNodeWithText("Connect").assertDoesNotExist()
        composeTestRule
            .onNodeWithText(
                "No answer from the Gateway. Your connection is saved; " +
                    "Mercurio retries on its own next time it starts.",
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("[ try again ]").performClick()

        assertEquals(EntryUiEvent.TryAgainClicked, events.single())
    }

    private fun gatewayFormState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_new),
            supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = "https://gateway.example",
            bearerCredential = "synthetic-token",
        )

    @Test
    fun switching_from_an_active_run_keeps_send_available_in_another_session() {
        val first = Session(SessionId("first"), "First Session", null, pinned = false)
        val second = Session(SessionId("second"), "Second Session", null, pinned = false)
        val activeRun = Run(RunId("run-first"), first.id, "running")
        val gateway = SwitchingGateway(first, second, activeRun)
        val recoveryRegistry = InMemoryRunRecoveryRegistry()
        val holder = switchingHolder(gateway, recoveryRegistry)

        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(
                    state = holder.uiState.collectAsState().value,
                    onEvent = holder::onEvent,
                )
            }
        }

        try {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged("https://gateway.example/profile"))
            holder.onEvent(EntryUiEvent.BearerCredentialChanged("memory-only-token"))
            holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
            awaitSessionVisible(holder, first)
            composeTestRule.onNodeWithText("First Session").performClick()
            composeTestRule.waitUntil(TEST_TIMEOUT_MILLIS) {
                holder.uiState.value.sessionList?.openedSession?.session?.id == first.id &&
                    holder.uiState.value.sessionList?.openedSession?.isReconciliationInProgress == false
            }
            composeTestRule.runOnIdle { holder.onEvent(EntryUiEvent.ComposerTextChanged("First request")) }
            composeTestRule.onNodeWithText("[ send ]").performClick()
            assertTrue(gateway.firstRunCreated.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertTrue(gateway.firstObservation.started.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertEquals(1L, gateway.firstObservation.closed.count)
            assertEquals(false, holder.uiState.value.sessionList?.openedSession?.hasUnresolvedSubmission)

            composeTestRule.runOnIdle { holder.onEvent(EntryUiEvent.ComposerTextChanged("First draft")) }
            composeTestRule.onNodeWithText("[ stop ]").assertIsEnabled()

            composeTestRule.onNodeWithContentDescription("Back to Sessions").performClick()
            assertTrue(gateway.firstObservation.closed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            composeTestRule.onNodeWithText("Second Session").performClick()
            composeTestRule.waitUntil(TEST_TIMEOUT_MILLIS) {
                holder.uiState.value.sessionList?.openedSession?.session?.id == second.id &&
                    holder.uiState.value.sessionList?.openedSession?.isReconciliationInProgress == false
            }
            composeTestRule.runOnIdle { holder.onEvent(EntryUiEvent.ComposerTextChanged("Second draft")) }
            composeTestRule.waitUntil(TEST_TIMEOUT_MILLIS) {
                holder.uiState.value.sessionList?.openedSession?.composerText == "Second draft"
            }
            composeTestRule.onNodeWithText("[ send ]").assertIsEnabled()
            composeTestRule.onNodeWithText("[ send ]").performClick()
            assertTrue(gateway.secondRunCreated.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

            composeTestRule.onNodeWithContentDescription("Back to Sessions").performClick()
            composeTestRule.onNodeWithText("First Session").performClick()
            composeTestRule.waitUntil(TEST_TIMEOUT_MILLIS) {
                holder.uiState.value.sessionList?.openedSession?.session?.id == first.id &&
                    holder.uiState.value.sessionList?.openedSession?.isReconciliationInProgress == false
            }
            composeTestRule.onNodeWithText("First draft").assertIsDisplayed()
            composeTestRule.onNodeWithText("[ send ]").assertIsNotEnabled()
        } finally {
            holder.close()
        }
    }

    private fun awaitSessionVisible(
        holder: EntryStateHolder,
        session: Session,
    ) {
        composeTestRule.waitUntil(TEST_TIMEOUT_MILLIS) {
            val list = holder.uiState.value.sessionList
            list != null && list.sessions.any { it.id == session.id } && !list.isLoading
        }
    }

    private class SwitchingGateway(
        private val first: Session,
        private val second: Session,
        private val activeRun: Run,
    ) : SessionGatewayPort, RunGatewayPort {
        val firstRunCreated = CountDownLatch(1)
        val secondRunCreated = CountDownLatch(1)
        val firstObservation = BlockingObservation()
        private val secondRun = Run(RunId("run-second"), second.id, "running")
        private var firstRunHasBeenCreated = false
        private var firstObservationRequested = false

        override fun listSessions(request: SessionListRequest): SessionPage =
            SessionPage(
                listOf(first, second),
                nextOffset = null,
            )

        override fun createSession(title: String?): Session = error("not used")

        override fun openSession(sessionId: SessionId): Session = listOf(first, second).single { it.id == sessionId }

        override fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            SessionHistory(
                sessionId = sessionId,
                messages =
                    if (sessionId == first.id && firstRunHasBeenCreated) {
                        listOf(
                            GatewayHistoryMessage(
                                id = "active-run",
                                role = "assistant",
                                content = "Still running",
                                runId = activeRun.id,
                                runStatus = activeRun.status,
                            ),
                        )
                    } else {
                        emptyList()
                    },
            )

        override fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session = error("not used")

        override fun deleteSession(sessionId: SessionId) = error("not used")

        override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override fun createRun(
            sessionId: SessionId,
            input: String,
        ): Run {
            return if (sessionId == first.id) {
                firstRunHasBeenCreated = true
                firstRunCreated.countDown()
                activeRun
            } else {
                secondRunCreated.countDown()
                secondRun
            }
        }

        override fun getRunStatus(runId: RunId): Run =
            when (runId) {
                activeRun.id -> activeRun
                secondRun.id -> secondRun
                else -> error("Unknown Run: $runId")
            }

        override fun observeRun(runId: RunId): RunEventObservation {
            if (runId == activeRun.id && !firstObservationRequested) {
                firstObservationRequested = true
                return firstObservation
            }
            return object : RunEventObservation {
                override fun iterator(): Iterator<RunEvent> = emptyList<RunEvent>().iterator()

                override fun close() = Unit
            }
        }
    }

    private class BlockingObservation : RunEventObservation {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        private val release = CountDownLatch(1)

        override fun iterator(): Iterator<RunEvent> =
            object : Iterator<RunEvent> {
                override fun hasNext(): Boolean {
                    started.countDown()
                    release.await(TEST_TIMEOUT_MILLIS * 6, TimeUnit.MILLISECONDS)
                    return false
                }

                override fun next(): RunEvent = error("not used")
            }

        override fun close() {
            closed.countDown()
            release.countDown()
        }
    }

    private class FakeGatewayConnectionRepository : GatewayConnectionRepository {
        override fun load(): GatewayConnection? = null

        override fun save(connection: GatewayConnection) = Unit
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }

    private fun removeBody(): String = copy(R.string.dialog_remove_connection_body)

    private fun switchingHolder(
        gateway: SwitchingGateway,
        recoveryRegistry: InMemoryRunRecoveryRegistry,
    ): EntryStateHolder =
        EntryStateHolder(
            initialState = EntryState(isGatewayConnectionConfigured = false),
            verifyGatewayConnection =
                VerifyGatewayConnection(FakeGatewayConnectionRepository()) { _, _ ->
                    GatewayCapabilities(
                        PublicBetaGatewayCapabilityManifest.current.requiredEndpoints,
                    )
                },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            sessionGatewayFactory = { _, _ -> gateway },
            runGatewayFactory = { _, _ -> gateway },
            dependencies =
                EntryStateHolderDependencies(
                    runRecoveryRegistry = recoveryRegistry,
                    persistRunRecoveryEntry = { _, entry -> recoveryRegistry.save(entry) },
                    removeRunRecoveryEntry = { _, entry -> recoveryRegistry.remove(entry) },
                ),
        )
}

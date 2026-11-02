package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectedVisualRegressionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight")
    fun error_reporting_consent_first_run_light() {
        setContent(consentState())
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun error_reporting_consent_first_run_dark() {
        setContent(consentState(), darkTheme = true)
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun error_reporting_consent_short_200_top_light() {
        setContentAtTwoHundredPercent(consentState(), darkTheme = false)
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun error_reporting_consent_short_200_top_dark() {
        setContentAtTwoHundredPercent(consentState(), darkTheme = true)
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun error_reporting_consent_short_200_answers_light() {
        setContentAtTwoHundredPercent(consentState(), darkTheme = false)
        composeTestRule.onNodeWithText(copy(R.string.consent_do_not_send)).performScrollTo()
        capture()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun error_reporting_consent_short_200_answers_dark() {
        setContentAtTwoHundredPercent(consentState(), darkTheme = true)
        composeTestRule.onNodeWithText(copy(R.string.consent_do_not_send)).performScrollTo()
        capture()
    }

    @Test
    fun connection_form() {
        setContent(setupFormState())
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_form_dark() {
        setContent(setupFormState(), darkTheme = true)
        capture()
    }

    @Test
    fun connection_error() {
        setContent(setupFormState(errorCategory = EntryErrorCategory.AUTHENTICATION_FAILED))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_error_dark() {
        setContent(
            setupFormState(errorCategory = EntryErrorCategory.AUTHENTICATION_FAILED),
            darkTheme = true,
        )
        capture()
    }

    @Test
    fun connection_saving_light() {
        setContent(setupFormState(isSaving = true))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_saving_dark() {
        setContent(setupFormState(isSaving = true), darkTheme = true)
        capture()
    }

    @Test
    fun connection_storage_retry_light() {
        setContent(setupFormState(errorCategory = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_storage_retry_dark() {
        setContent(
            setupFormState(errorCategory = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED),
            darkTheme = true,
        )
        capture()
    }

    @Test
    fun connection_replacement_ready_light() {
        setContent(replacementFormState())
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_replacement_ready_dark() {
        setContent(replacementFormState(), darkTheme = true)
        capture()
    }

    @Test
    fun connection_remove_confirmation_light() {
        setContent(removeConfirmationState())
        captureDialog()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun connection_remove_confirmation_dark() {
        setContent(removeConfirmationState(), darkTheme = true)
        captureDialog()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp-notnight")
    fun connection_remove_confirmation_wide_light() {
        setContent(removeConfirmationState())
        captureDialog()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp-night")
    fun connection_remove_confirmation_wide_dark() {
        setContent(removeConfirmationState(), darkTheme = true)
        captureDialog()
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp-notnight")
    fun connection_storage_retry_short_ime_200_light() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme(darkTheme = false) {
                    EntryScreen(
                        state = setupFormState(errorCategory = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED),
                        onEvent = {},
                    )
                }
            }
        }
        composeTestRule
            .onNodeWithText(copy(EntryErrorCategory.CREDENTIAL_STORAGE_FAILED.messageRes))
            .performScrollTo()
        capture()
    }

    @Test
    fun session_list_populated_light() {
        setContent(connectedState(SessionListUiState(sessions = designSessions())))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_populated_dark() {
        setContent(connectedState(SessionListUiState(sessions = designSessions())), darkTheme = true)
        capture()
    }

    @Test
    fun session_list_selected_row_light() {
        selectedRowPane(darkTheme = false)
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_selected_row_dark() {
        selectedRowPane(darkTheme = true)
        capture()
    }

    @Test
    fun session_list_empty_light() {
        setContent(connectedState(SessionListUiState()))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_empty_dark() {
        setContent(connectedState(SessionListUiState()), darkTheme = true)
        capture()
    }

    @Test
    fun session_list_no_match_light() {
        setContent(connectedState(SessionListUiState(sessions = designSessions(), searchQuery = "release")))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_no_match_dark() {
        setContent(
            connectedState(SessionListUiState(sessions = designSessions(), searchQuery = "release")),
            darkTheme = true,
        )
        capture()
    }

    @Test
    fun session_list_loading_light() {
        setContent(connectedState(SessionListUiState(isLoading = true)))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_loading_dark() {
        setContent(connectedState(SessionListUiState(isLoading = true)), darkTheme = true)
        capture()
    }

    @Test
    fun session_list_unavailable_light() {
        setContent(connectedState(unavailableListState()))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_unavailable_dark() {
        setContent(connectedState(unavailableListState()), darkTheme = true)
        capture()
    }

    @Test
    fun session_list_pending_title_light() {
        setContent(connectedState(SessionListUiState(sessions = pendingTitleSessions())))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_pending_title_dark() {
        setContent(connectedState(SessionListUiState(sessions = pendingTitleSessions())), darkTheme = true)
        capture()
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    fun session_list_row_actions_light() {
        setContent(connectedState(SessionListUiState(sessions = designSessions())))
        composeTestRule.onNodeWithText("Gateway connection check").performTouchInput { longClick() }
        captureScreen()
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_row_actions_dark() {
        setContent(connectedState(SessionListUiState(sessions = designSessions())), darkTheme = true)
        composeTestRule.onNodeWithText("Gateway connection check").performTouchInput { longClick() }
        captureScreen()
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    fun session_list_delete_confirmation_light() {
        setContent(connectedState(deleteConfirmationState()))
        captureScreen()
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_delete_confirmation_dark() {
        setContent(connectedState(deleteConfirmationState()), darkTheme = true)
        captureScreen()
    }

    @Test
    fun session_list_refreshing_light() {
        setContent(connectedState(SessionListUiState(sessions = designSessions(), isRefreshing = true)))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_refreshing_dark() {
        setContent(
            connectedState(SessionListUiState(sessions = designSessions(), isRefreshing = true)),
            darkTheme = true,
        )
        capture()
    }

    @Test
    fun session_list_loading_more_light() {
        setContent(
            connectedState(
                SessionListUiState(sessions = designSessions(), isLoadingMore = true, nextOffset = 20),
            ),
        )
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_loading_more_dark() {
        setContent(
            connectedState(
                SessionListUiState(sessions = designSessions(), isLoadingMore = true, nextOffset = 20),
            ),
            darkTheme = true,
        )
        capture()
    }

    @Test
    fun session_list_rename_light() {
        setContent(connectedState(renameListState()))
        capture()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun session_list_rename_dark() {
        setContent(connectedState(renameListState()), darkTheme = true)
        capture()
    }

    private fun designSessions(): List<SessionItemUiState> =
        listOf(
            session("nightly", "Nightly pipeline triage", "Two failing tests; retry green", pinned = true, time = "1h"),
            session("tokens", "Design system tokens", "Primary, surface and outline roles", pinned = true, time = "2d"),
            session(
                id = "new",
                title = "",
                preview = null,
                time = "now",
                pending = true,
            ),
            session("gateway", "Gateway connection check", "Endpoint and stored credential", time = "30m"),
            session("release", "Release checklist", "0.9.3 after the review window", time = "1d"),
            session("vision", "Vision capture backlog", "Six screens need fresh captures", time = "1w"),
            session("routing", "Model routing notes", "Aux routes stay on the flash tier", time = "2w"),
        )

    private fun pendingTitleSessions(): List<SessionItemUiState> =
        designSessions().filterNot { it.id.value == "new" } +
            session(
                id = "new",
                title = "",
                preview = null,
                time = "now",
                pending = true,
            )

    private fun unavailableListState(): SessionListUiState =
        SessionListUiState(
            sessions = designSessions().take(4),
            isStale = true,
            isUnavailable = true,
            isCached = true,
            errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
        )

    private fun deleteConfirmationState(): SessionListUiState =
        SessionListUiState(
            sessions = designSessions(),
            sessionMutations =
                mapOf(
                    SessionId("tokens") to
                        SessionMutationUiState(
                            delete = SessionDeleteUiState(),
                        ),
                ),
        )

    private fun renameListState(): SessionListUiState =
        SessionListUiState(
            sessions = designSessions(),
            sessionMutations =
                mapOf(
                    SessionId("tokens") to
                        SessionMutationUiState(
                            rename = SessionRenameUiState(titleDraft = "Design system tok"),
                        ),
                ),
        )

    private fun selectedRowPane(darkTheme: Boolean): Unit =
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme) {
                SessionListPane(
                    state = SessionListUiState(sessions = designSessions()),
                    onEvent = {},
                    modifier = Modifier.fillMaxSize(),
                    selectedSessionId = SessionId("gateway"),
                )
            }
        }

    @Test
    fun empty_session_detail() {
        setContent(
            connectedState(
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = session("empty-session", "New Session"),
                            messages = emptyList(),
                        ),
                ),
            ),
        )

        capture()
    }

    @Test
    fun session_detail_with_messages() {
        setContent(
            EntryUiState(
                title = UiText.Resource(R.string.entry_title_connected),
                supportingText = UiText.Value(""),
                actionLabel = UiText.Value(""),
                sessionList =
                    SessionListUiState(
                        openedSession =
                            OpenSessionUiState(
                                session =
                                    SessionItemUiState(
                                        id = SessionId("fixture-session"),
                                        title = "Release checklist",
                                        preview = "Review the release checklist.",
                                        pinned = true,
                                    ),
                                messages =
                                    listOf(
                                        SessionMessageUiState(
                                            id = "message-1",
                                            role = "user",
                                            content = "Show the release checklist.",
                                        ),
                                        SessionMessageUiState(
                                            id = "message-2",
                                            role = "assistant",
                                            content = "The release checklist is ready.",
                                        ),
                                    ),
                                composerText = "Summarize the next step",
                            ),
                    ),
            ),
        )

        capture()
    }

    @Test
    fun session_detail_overflowing_transcript() {
        setContent(
            connectedState(
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = session("overflow-session", "Overflowing Session"),
                            messages =
                                (1..12).map { index ->
                                    message("message-$index", "user", "Transcript message $index")
                                },
                        ),
                ),
            ),
        )

        capture()
    }

    @Test
    fun session_detail_active_run() {
        val sessionId = SessionId("active-session")
        val runId = RunId("active-run")
        setContent(
            connectedState(
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = session("active-session", "Active Session"),
                            messages = emptyList(),
                            composerText = "Follow-up question",
                            latestRun = Run(runId, sessionId, "running"),
                            latestRunState = RunPresentationState.RUNNING,
                            activeResponse =
                                SessionMessageUiState(
                                    id = "active-response",
                                    role = "assistant",
                                    content = "Partial response",
                                    runId = runId,
                                    runState = RunPresentationState.RUNNING,
                                    isStreaming = true,
                                ),
                        ),
                ),
            ),
        )

        capture()
    }

    @Test
    fun session_detail_error() {
        setContent(
            connectedState(
                SessionListUiState(
                    openedSession =
                        OpenSessionUiState(
                            session = session("failed-session", "Retry Session"),
                            messages = emptyList(),
                            composerText = "Keep this draft",
                            sendErrorCategory = MessageSendErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                ),
            ),
        )

        capture()
    }

    private fun setupFormState(
        isSaving: Boolean = false,
        errorCategory: EntryErrorCategory? = null,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_new),
            supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = "https://gateway.example/profile",
            bearerCredential = "fixture-credential",
            isSaving = isSaving,
            errorCategory = errorCategory,
        )

    private fun replacementFormState(errorCategory: EntryErrorCategory? = null): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.action_change_credential),
            supportingText = UiText.Resource(R.string.entry_supporting_change_credential),
            actionLabel = UiText.Resource(R.string.entry_action_verify_and_save),
            connectionSetupRequested = true,
            isChangingCredential = true,
            isConnected = true,
            endpoint = "https://gateway.example/profile",
            bearerCredential = "replacement-credential",
            errorCategory = errorCategory,
        )

    private fun removeConfirmationState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            sessionList =
                SessionListUiState(
                    sessions =
                        listOf(
                            session(
                                id = "pinned-session",
                                title = "Release checklist",
                                preview = "Review the release checklist.",
                                pinned = true,
                            ),
                        ),
                ),
            isRemoveConfirmationOpen = true,
        )

    private fun setContent(
        state: EntryUiState,
        darkTheme: Boolean? = null,
    ) {
        composeTestRule.setContent {
            HermesTheme(darkTheme = darkTheme ?: isSystemInDarkTheme()) {
                EntryScreen(state = state, onEvent = {})
            }
        }
    }

    private fun setContentAtTwoHundredPercent(
        state: EntryUiState,
        darkTheme: Boolean,
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HermesTheme(darkTheme = darkTheme) {
                    EntryScreen(state = state, onEvent = {})
                }
            }
        }
    }

    private fun consentState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connect),
            supportingText = UiText.Resource(R.string.entry_supporting_connect),
            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
            isErrorReportingConsentRequired = true,
        )

    private fun connectedState(sessionList: SessionListUiState): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            sessionList = sessionList,
        )

    @Suppress("LongParameterList")
    private fun session(
        id: String,
        title: String,
        preview: String? = null,
        pinned: Boolean = false,
        time: String = "—",
        pending: Boolean = false,
    ): SessionItemUiState =
        SessionItemUiState(
            id = SessionId(id),
            title = title,
            preview = preview,
            pinned = pinned,
            lastActive = relativeLastActive(time),
            isPendingTitle = pending,
        )

    private fun capture() {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureDialog() {
        composeTestRule.waitForIdle()
        composeTestRule.onNode(isDialog()).captureRoboImage()
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun captureScreen() {
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }
}

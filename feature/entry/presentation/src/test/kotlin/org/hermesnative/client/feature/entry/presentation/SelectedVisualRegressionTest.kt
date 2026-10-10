package org.hermesnative.client.feature.entry.presentation

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.RunPresentationState
import org.hermesnative.client.feature.entry.domain.SessionId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    fun connection_form() {
        setContent(
            EntryUiState(
                title = "Connect to a Gateway",
                supportingText = "Add a Gateway connection to start using Hermes.",
                actionLabel = "Verify connection",
                connectionSetupRequested = true,
                endpoint = "https://gateway.example.test",
                bearerCredential = "fixture-credential",
            ),
        )

        capture()
    }

    @Test
    fun connection_error() {
        setContent(
            EntryUiState(
                title = "Verify a Hermes Gateway",
                supportingText = "Correct the details and try again.",
                actionLabel = "Verify Gateway Connection",
                connectionSetupRequested = true,
                endpoint = "https://gateway.example.test",
                bearerCredential = "fixture-credential",
                errorCategory = EntryErrorCategory.AUTHENTICATION_FAILED,
            ),
        )

        capture()
    }

    @Test
    fun empty_session_list() {
        setContent(
            EntryUiState(
                title = "Gateway connected",
                supportingText = "",
                actionLabel = "",
                sessionList = SessionListUiState(showFirstUseGuidance = true),
            ),
        )

        capture()
    }

    @Test
    fun populated_session_list() {
        setContent(
            connectedState(
                SessionListUiState(
                    sessions =
                        listOf(
                            session(
                                id = "pinned-session",
                                title = "Release checklist",
                                preview = "Review the release checklist.",
                                pinned = true,
                            ),
                            session(
                                id = "follow-up-session",
                                title = "Follow-up Session",
                                preview = "Plan the next step.",
                            ),
                        ),
                ),
            ),
        )

        capture()
    }

    @Test
    fun session_list_unavailable() {
        setContent(
            connectedState(
                SessionListUiState(
                    isStale = true,
                    isUnavailable = true,
                    errorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
                ),
            ),
        )

        capture()
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session() {
        setContent(
            connectedState(
                SessionListUiState(
                    createSession = SessionCreationUiState(titleDraft = "Release checklist"),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session_empty() {
        setContent(
            connectedState(
                SessionListUiState(
                    createSession = SessionCreationUiState(),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun create_session_failure() {
        setContent(
            connectedState(
                SessionListUiState(
                    createSession =
                        SessionCreationUiState(
                            titleDraft = "Release checklist",
                            errorCategory = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun create_session_dark() {
        setContent(
            connectedState(
                SessionListUiState(
                    createSession = SessionCreationUiState(titleDraft = "Release checklist"),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w1000dp-h800dp-notnight")
    fun create_session_two_pane() {
        setContent(
            connectedState(
                SessionListUiState(
                    sessions =
                        listOf(
                            session("release-checklist", "Release checklist", "Review the release checklist."),
                        ),
                    createSession = SessionCreationUiState(titleDraft = "Release checklist"),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * The smallest deterministic constrained-height witness: at twice the font scale in a very
     * short window, the bounded dialog text region scrolls (long draft plus the failure message)
     * instead of pushing the Create/Cancel row out of the dialog.
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w411dp-h289dp-notnight")
    fun create_session_short_window_long_error() {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.fontScale = 2f
        application.resources.updateConfiguration(configuration, application.resources.displayMetrics)

        setContent(
            connectedState(
                SessionListUiState(
                    createSession =
                        SessionCreationUiState(
                            titleDraft = "A deliberately long draft title that the input must keep reachable",
                            errorCategory = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                ),
            ),
        )

        captureScreen()
    }

    /**
     * The justified end state of the same witness: the bounded text region scrolls to the
     * retained failure message while the action row stays fixed and visible.
     * Robolectric's paused looper cannot settle the standard dialog window with its text field
     * (robolectric/robolectric#8460). Only the looper mode changes here; the captured dialog is
     * the same production composition the device journeys exercise.
     */
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    @Config(qualifiers = "w411dp-h289dp-notnight")
    fun create_session_short_window_long_error_scrolled() {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.fontScale = 2f
        application.resources.updateConfiguration(configuration, application.resources.displayMetrics)

        setContent(
            connectedState(
                SessionListUiState(
                    createSession =
                        SessionCreationUiState(
                            titleDraft = "A deliberately long draft title that the input must keep reachable",
                            errorCategory = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED,
                        ),
                ),
            ),
        )

        composeTestRule
            .onNodeWithText(SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED.safeMessage)
            .performScrollTo()
        captureScreen()
    }

    @Test
    fun rename_session_confirmation() {
        val sessionId = SessionId("rename-session")
        setContent(
            connectedState(
                SessionListUiState(
                    sessions = listOf(session("rename-session", "Current title", "Current preview")),
                    sessionMutations =
                        mapOf(
                            sessionId to
                                SessionMutationUiState(
                                    rename = SessionRenameUiState(titleDraft = "Updated title"),
                                ),
                        ),
                ),
            ),
        )

        capture()
    }

    @Test
    fun delete_session_confirmation() {
        val sessionId = SessionId("delete-session")
        setContent(
            connectedState(
                SessionListUiState(
                    sessions = listOf(session("delete-session", "Remote Session", "Remote preview")),
                    sessionMutations =
                        mapOf(
                            sessionId to
                                SessionMutationUiState(
                                    delete = SessionDeleteUiState(),
                                ),
                        ),
                ),
            ),
        )

        capture()
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
                title = "Gateway connected",
                supportingText = "",
                actionLabel = "",
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

    private fun setContent(state: EntryUiState) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = state, onEvent = {})
            }
        }
    }

    private fun connectedState(sessionList: SessionListUiState): EntryUiState =
        EntryUiState(
            title = "Gateway connected",
            supportingText = "",
            actionLabel = "",
            sessionList = sessionList,
        )

    private fun session(
        id: String,
        title: String,
        preview: String? = null,
        pinned: Boolean = false,
    ): SessionItemUiState =
        SessionItemUiState(
            id = SessionId(id),
            title = title,
            preview = preview,
            pinned = pinned,
        )

    private fun capture() {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun captureScreen() {
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }
}

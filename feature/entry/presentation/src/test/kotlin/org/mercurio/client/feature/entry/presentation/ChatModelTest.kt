package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId
import java.time.Instant
import java.time.ZoneId

/**
 * The approved Chat design's derivations: the day separators and row labels the transcript
 * prints, the run line's state word and its one right segment, and the composer's reason.
 */
class ChatModelTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun the_transcript_prints_one_day_separator_per_day_in_order() {
        val items =
            chatTranscriptItems(
                messages =
                    listOf(
                        message("message-1", "user", "First day", Instant.parse("2026-11-01T08:12:00Z")),
                        message("message-2", "assistant", "Still the first day", Instant.parse("2026-11-01T08:13:00Z")),
                        message("message-3", "user", "Second day", Instant.parse("2026-11-02T08:12:00Z")),
                    ),
                zone = zone,
            )

        val days = items.filterIsInstance<ChatTranscriptItem.Day>().map { day -> day.label }
        assertEquals(listOf("SUN 01 NOV", "MON 02 NOV"), days)
        assertEquals(5, items.size)
    }

    @Test
    fun a_row_without_a_timestamp_stays_under_the_day_before_it() {
        val items =
            chatTranscriptItems(
                messages =
                    listOf(
                        message("message-1", "user", "First", Instant.parse("2026-11-01T08:12:00Z")),
                        SessionMessageUiState("local-user:run", "user", "Pending"),
                    ),
                zone = zone,
            )

        assertEquals(1, items.filterIsInstance<ChatTranscriptItem.Day>().size)
        val rows = items.filterIsInstance<ChatTranscriptItem.Message>().map { item -> item.row }
        val timeLabels = rows.map { row -> row.timeLabel }
        assertEquals(listOf(UiText.Value("09:12"), UiText.Resource(R.string.chat_time_now)), timeLabels)
        assertEquals(ChatRowKind.Pending, rows.last().kind)
    }

    @Test
    fun a_failed_turn_is_its_own_row_with_the_safe_message() {
        val row =
            chatRow(
                SessionMessageUiState(
                    id = "message-failed",
                    role = "assistant",
                    content = null,
                    runId = RunId("4b2a-run"),
                    runState = RunPresentationState.FAILED,
                    failureMessage = UiText.Resource(R.string.error_run_failed),
                ),
                zone,
            )

        assertEquals(ChatRowKind.Failed, row.kind)
        assertEquals(UiText.Resource(R.string.chat_row_failed), row.label)
        assertEquals(uiText(R.string.chat_run_label, "4b2a"), row.runIdLabel)
        assertNull(row.payload)
    }

    @Test
    fun an_unknown_role_stays_a_system_row_with_its_own_label() {
        val row = chatRow(SessionMessageUiState("message", "tool", "Tool output"), zone)

        assertEquals(ChatRowKind.System, row.kind)
        assertEquals(UiText.Value("Tool"), row.label)
    }

    @Test
    fun the_idle_run_line_reports_the_gateway_and_the_message_count() {
        val line = chatRunLineState(detail(content(messages = listOf(message("message-1", "user", "One")))))

        assertEquals(UiText.Resource(R.string.chat_run_state_idle), line.state)
        assertEquals(uiText(R.string.chat_run_messages, 1), line.dim)
        assertFalse(line.error)
        assertTrue(line.right is ChatRunRight.Gateway)
    }

    @Test
    fun a_streaming_run_line_offers_stop_and_nothing_else() {
        val runId = RunId("4b2a-run")
        val line =
            chatRunLineState(
                detail(
                    content(
                        latestRun = Run(runId, SessionId("session"), "running"),
                        latestRunState = RunPresentationState.RUNNING,
                        activeResponse =
                            SessionMessageUiState(
                                "active",
                                "assistant",
                                "Writing",
                                runId = runId,
                                isStreaming = true,
                            ),
                    ),
                ),
            )

        assertEquals(uiText(R.string.chat_run_state_streaming, "4b2a"), line.state)
        assertEquals(ChatRunRight.Action(UiText.Resource(R.string.chat_stop), EntryUiEvent.StopRunClicked), line.right)
        assertTrue(line.active)
    }

    @Test
    fun a_stale_run_line_offers_the_history_refresh_and_marks_the_error() {
        val runId = RunId("4b2a-run")
        val line =
            chatRunLineState(
                detail(
                    content(
                        latestRun = Run(runId, SessionId("session"), "interrupted"),
                        latestRunState = RunPresentationState.UNCERTAIN,
                        isStale = true,
                    ),
                ),
            )

        assertEquals(uiText(R.string.chat_run_state_stale, "4b2a"), line.state)
        val expectedAction =
            ChatRunRight.Action(
                UiText.Resource(R.string.chat_refresh_history),
                EntryUiEvent.RefreshSessionsClicked,
            )
        assertEquals(expectedAction, line.right)
        assertTrue(line.error)
    }

    @Test
    fun an_unreachable_session_without_a_cache_reports_an_offline_gateway() {
        val line =
            chatRunLineState(
                detail(
                    content(messages = emptyList(), errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED),
                ),
            )

        assertEquals(UiText.Resource(R.string.chat_run_state_history_unavailable), line.state)
        assertEquals(ChatRunRight.Gateway(online = false), line.right)
    }

    @Test
    fun the_composer_states_the_reason_it_cannot_send() {
        val composer = chatComposerState(detail(content(messages = emptyList(), isRefreshing = true)))

        assertFalse(composer.fieldEnabled)
        assertFalse(composer.sendEnabled)
        assertEquals(UiText.Resource(R.string.chat_reason_history_loading), composer.reason)
    }

    @Test
    fun a_live_run_disables_the_action_but_keeps_the_field_editable() {
        val runId = RunId("4b2a-run")
        val composer =
            chatComposerState(
                detail(
                    content(
                        composerText = "Draft",
                        latestRun = Run(runId, SessionId("session"), "running"),
                        latestRunState = RunPresentationState.RUNNING,
                    ),
                ),
            )

        assertTrue(composer.fieldEnabled)
        assertFalse(composer.sendEnabled)
        assertNull(composer.reason)
    }

    private fun message(
        id: String,
        role: String,
        content: String,
        timestamp: Instant? = null,
    ): SessionMessageUiState = SessionMessageUiState(id = id, role = role, content = content, timestamp = timestamp)

    @Suppress("LongParameterList")
    private fun content(
        messages: List<SessionMessageUiState> = emptyList(),
        composerText: String = "",
        isRefreshing: Boolean = false,
        isStale: Boolean = false,
        isCached: Boolean = false,
        errorCategory: SessionHistoryErrorCategory? = null,
        latestRun: Run? = null,
        latestRunState: RunPresentationState? = null,
        activeResponse: SessionMessageUiState? = null,
        isReconciliationInProgress: Boolean = false,
    ): OpenSessionUiState =
        OpenSessionUiState(
            session = SessionItemUiState(SessionId("session"), "Session", null, false),
            messages = messages,
            composerText = composerText,
            isRefreshing = isRefreshing,
            isStale = isStale,
            isCached = isCached,
            errorCategory = errorCategory,
            latestRun = latestRun,
            latestRunState = latestRunState,
            activeResponse = activeResponse,
            isReconciliationInProgress = isReconciliationInProgress,
        )

    private fun detail(content: OpenSessionUiState): SessionDetailState =
        SessionDetailState(
            content = content,
            mutation = null,
            actionsEnabled = true,
            listRequestActive = false,
            listIsStale = false,
            listErrorCategory = null,
        )
}

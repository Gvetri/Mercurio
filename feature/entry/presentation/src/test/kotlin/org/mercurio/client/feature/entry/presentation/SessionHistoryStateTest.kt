package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.SessionId
import java.time.Instant

class SessionHistoryStateTest {
    @Test
    fun message_mapper_preserves_gateway_identity_and_optional_run_metadata() {
        val mapped =
            GatewayHistoryMessage(
                id = "message-1",
                role = "user",
                content = "Run this",
                runId = RunId("run-1"),
                runStatus = "completed",
                runResult = "Done",
                timestamp = "2026-09-08T20:00:00Z",
            ).toSessionMessageUiState()

        assertEquals("message-1", mapped.id)
        assertEquals(RunId("run-1"), mapped.runId)
        assertEquals("completed", mapped.runStatus)
        assertEquals("Done", mapped.runResult)
        assertEquals(Instant.parse("2026-09-08T20:00:00Z"), mapped.timestamp)
    }

    @Test
    fun invalid_optional_timestamp_is_not_invented() {
        assertNull(mappedTimestamp("not-a-timestamp"))
    }

    @Test
    fun both_pinned_timestamp_forms_produce_a_usable_parsed_value() {
        val expectedByForm =
            mapOf(
                "2026-09-08T20:00:00Z" to Instant.parse("2026-09-08T20:00:00Z"),
                "1757325600" to Instant.ofEpochSecond(1757325600L),
                "1757325600.0" to Instant.ofEpochSecond(1757325600L),
                "1790949057.5903823" to Instant.ofEpochSecond(1790949057L, 590382300L),
            )

        expectedByForm.forEach { (timestamp, expected) ->
            assertEquals(timestamp, expected, mappedTimestamp(timestamp))
        }
    }

    @Test
    fun non_finite_numeric_timestamps_stay_unavailable() {
        listOf("1e309", "NaN", "Infinity").forEach { timestamp ->
            assertNull(timestamp, mappedTimestamp(timestamp))
        }
    }

    @Test
    fun missing_timestamp_is_never_fabricated() {
        assertNull(mappedTimestamp(null))
    }

    @Test
    fun fully_timestamped_history_is_chronological() {
        val ordered =
            listOf(
                SessionMessageUiState("later", "assistant", "Later", timestamp = Instant.parse("2026-09-08T21:00:00Z")),
                SessionMessageUiState("earlier", "user", "Earlier", timestamp = Instant.parse("2026-09-08T20:00:00Z")),
            ).chronological()

        assertEquals(listOf("earlier", "later"), ordered.map { it.id })
    }

    @Test
    fun history_with_unavailable_timestamp_preserves_gateway_order() {
        val ordered =
            listOf(
                SessionMessageUiState("later", "assistant", "Later", timestamp = Instant.parse("2026-09-08T21:00:00Z")),
                SessionMessageUiState("unknown", "assistant", "Unknown"),
                SessionMessageUiState("earlier", "user", "Earlier", timestamp = Instant.parse("2026-09-08T20:00:00Z")),
            ).chronological()

        assertEquals(listOf("earlier", "unknown", "later"), ordered.map { it.id })
    }

    @Test
    fun composer_is_editable_without_a_submission_event() {
        val state =
            SessionListUiState(
                openedSession =
                    OpenSessionUiState(
                        session = SessionItemUiState(SessionId("session-1"), "Session", null, false),
                        messages = emptyList(),
                    ),
            )

        assertEquals("", state.openedSession?.composerText)
    }

    private fun mappedTimestamp(raw: String?): Instant? =
        GatewayHistoryMessage(
            id = "message-1",
            role = "assistant",
            content = "Reply",
            timestamp = raw,
        ).toSessionMessageUiState().timestamp
}

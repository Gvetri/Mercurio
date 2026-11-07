package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which message row variant the approved Chat design draws. */
internal enum class ChatRowKind { Assistant, User, System, Failed, Pending }

/**
 * One message row of the approved Chat design, derived once from the Gateway state so the
 * renderer never decides a role, a label, or a time on its own.
 */
internal data class ChatRow(
    val id: String,
    val kind: ChatRowKind,
    val label: UiText,
    val timeLabel: UiText?,
    val body: String?,
    val failure: UiText?,
    val technicalDetail: String?,
    val retryAvailable: Boolean,
    val streaming: Boolean,
    val runId: RunId?,
    val runIdLabel: UiText?,
    val timestamp: Instant?,
    val payload: String?,
)

/** A transcript entry: either a day separator or one message row. */
internal sealed interface ChatTranscriptItem {
    data class Day(
        val label: String,
    ) : ChatTranscriptItem

    data class Message(
        val row: ChatRow,
    ) : ChatTranscriptItem
}

internal const val CHAT_LOCAL_MESSAGE_PREFIX = "local-user:"

internal const val CHAT_RUN_LABEL_LENGTH = 4

private val CHAT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val CHAT_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault())

private val CHAT_MENU_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault())

/**
 * Groups the transcript by day in the order the Messages already hold, so each separator
 * names the day its following rows belong to. Tool and function rows are not transcript
 * entries, so they never reach a day separator or a card. A row without a timestamp, such
 * as the local pending Message, stays under the day of the rows before it.
 */
internal fun chatTranscriptItems(
    messages: List<SessionMessageUiState>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ChatTranscriptItem> {
    val items = mutableListOf<ChatTranscriptItem>()
    var currentDay: LocalDate? = null
    messages.filterNot { message -> message.isToolResult() }.forEach { message ->
        val day = message.timestamp?.let { timestamp -> LocalDate.ofInstant(timestamp, zone) }
        if (day != null && day != currentDay) {
            items += ChatTranscriptItem.Day(label = day.format(CHAT_DAY_FORMAT).uppercase(Locale.getDefault()))
            currentDay = day
        }
        items += ChatTranscriptItem.Message(row = chatRow(message, zone))
    }
    return items
}

/** The row variant, its label and its time cell for one Message. */
internal fun chatRow(
    message: SessionMessageUiState,
    zone: ZoneId = ZoneId.systemDefault(),
): ChatRow {
    val payload = message.content?.takeIf { content -> content.isNotBlank() && message.failureMessage == null }
    val kind = chatRowKind(message)
    return ChatRow(
        id = message.id,
        kind = kind,
        label = chatRowLabel(kind, message.role),
        timeLabel = chatRowTime(message, zone),
        body = message.content?.takeIf(String::isNotBlank),
        failure = message.failureMessage,
        technicalDetail = message.failureTechnicalDetail,
        retryAvailable = message.retryAvailable,
        streaming = message.isStreaming,
        runId = message.runId,
        runIdLabel = message.runId?.let(::chatRunLabel),
        timestamp = message.timestamp,
        payload = payload,
    )
}

/** The full timestamp the message action menu prints under its items. */
internal fun chatMenuTimestamp(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String = instant.atZone(zone).format(CHAT_MENU_FORMAT)

/** The exact time the message action menu prints beside the date. */
internal fun chatMenuTime(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String = instant.atZone(zone).format(CHAT_TIME_FORMAT)

private fun SessionMessageUiState.isToolResult(): Boolean =
    role.equals("tool", ignoreCase = true) || role.equals("function", ignoreCase = true)

private fun chatRowKind(message: SessionMessageUiState): ChatRowKind =
    when {
        message.id.startsWith(CHAT_LOCAL_MESSAGE_PREFIX) -> ChatRowKind.Pending
        message.failureMessage != null || message.isFailedRun -> ChatRowKind.Failed
        message.role.equals("user", ignoreCase = true) -> ChatRowKind.User
        message.role.equals("assistant", ignoreCase = true) -> ChatRowKind.Assistant
        message.role.equals("agent", ignoreCase = true) -> ChatRowKind.Assistant
        else -> ChatRowKind.System
    }

private fun chatRowLabel(
    kind: ChatRowKind,
    role: String?,
): UiText =
    when (kind) {
        ChatRowKind.User, ChatRowKind.Pending -> UiText.Resource(R.string.chat_row_user)
        ChatRowKind.Assistant -> UiText.Resource(R.string.chat_row_assistant)
        ChatRowKind.Failed -> UiText.Resource(R.string.chat_row_failed)
        ChatRowKind.System ->
            role?.takeIf(String::isNotBlank)?.replaceFirstChar(Char::uppercase)?.let(UiText::Value)
                ?: UiText.Resource(R.string.chat_row_system)
    }

private fun chatRowTime(
    message: SessionMessageUiState,
    zone: ZoneId,
): UiText? =
    when {
        message.id.startsWith(CHAT_LOCAL_MESSAGE_PREFIX) -> UiText.Resource(R.string.chat_time_now)
        message.timestamp == null -> null
        else -> message.timestamp.atZone(zone).format(CHAT_TIME_FORMAT).let(UiText::Value)
    }

internal fun chatRunLabel(runId: RunId): UiText =
    uiText(
        R.string.chat_run_label,
        runId.value.take(CHAT_RUN_LABEL_LENGTH),
    )

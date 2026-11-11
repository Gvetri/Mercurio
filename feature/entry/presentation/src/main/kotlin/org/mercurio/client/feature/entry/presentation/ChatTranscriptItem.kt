package org.mercurio.client.feature.entry.presentation

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A transcript entry: either a day separator or one message row. */
internal sealed interface ChatTranscriptItem {
    data class Day(
        val label: String,
    ) : ChatTranscriptItem

    data class Message(
        val row: ChatRow,
    ) : ChatTranscriptItem
}

private val CHAT_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault())

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

private fun SessionMessageUiState.isToolResult(): Boolean =
    role.equals("tool", ignoreCase = true) || role.equals("function", ignoreCase = true)

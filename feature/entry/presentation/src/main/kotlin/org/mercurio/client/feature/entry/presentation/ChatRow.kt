package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

internal const val CHAT_LOCAL_MESSAGE_PREFIX = "local-user:"

internal const val CHAT_RUN_LABEL_LENGTH = 4

internal val CHAT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

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

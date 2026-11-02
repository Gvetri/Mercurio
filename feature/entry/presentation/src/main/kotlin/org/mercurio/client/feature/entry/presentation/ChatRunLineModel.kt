package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunPresentationState

/** The run line's right segment: the Gateway state, or the one offered action. */
internal sealed interface ChatRunRight {
    data class Gateway(
        val online: Boolean,
    ) : ChatRunRight

    data class Action(
        val label: String,
        val event: EntryUiEvent,
    ) : ChatRunRight
}

/** The run line under the transcript: state left, dim segment, one right segment. */
internal data class ChatRunLineState(
    val state: String,
    val dim: String?,
    val right: ChatRunRight,
    val active: Boolean,
    val error: Boolean,
)

internal const val CHAT_STOP_LABEL = "[ stop ]"

internal const val CHAT_REFRESH_LABEL = "[ refresh ]"

internal const val CHAT_REFRESH_HISTORY_LABEL = "[ refresh history ]"

/**
 * The run line for the current Session state. The state word is the one the design fixes:
 * `idle`, `sending`, `history · loading`, `cached · refreshing`, `cached · unavailable`,
 * `history · unavailable`, `reconciling run state`, and `run <id> · streaming|failed|stale`.
 * Stop lives here, so the composer action never shares its coordinate.
 */
internal fun chatRunLineState(detail: SessionDetailState): ChatRunLineState {
    val state = detail.content
    val runLabel = state.activeRunLabel() ?: state.latestRun?.let { run -> chatRunLabel(run.id) }
    val gateway = ChatRunRight.Gateway(online = detail.chatGatewayOnline())
    return state.ownedRunLine(runLabel, gateway) ?: state.historyLine(gateway)
}

@Suppress("CyclomaticComplexMethod")
private fun OpenSessionUiState.ownedRunLine(
    runLabel: String?,
    gateway: ChatRunRight.Gateway,
): ChatRunLineState? =
    when {
        isReconciliationInProgress ->
            ChatRunLineState("reconciling run state", null, gateway, active = true, error = false)
        chatStreamingRun() ->
            ChatRunLineState(
                state = "${runLabel.orEmpty()} · streaming".trim(),
                dim = null,
                right = ChatRunRight.Action(CHAT_STOP_LABEL, EntryUiEvent.StopRunClicked),
                active = true,
                error = false,
            )
        isSending ->
            ChatRunLineState(
                "sending",
                "· ${chatPendingCount().coerceAtLeast(1)} pending",
                gateway,
                active = true,
                error = false,
            )
        latestRunState == RunPresentationState.FAILED ->
            ChatRunLineState(
                "${runLabel.orEmpty()} · failed".trim(),
                null,
                gateway,
                active = false,
                error = true,
            )
        chatStaleRun() ->
            ChatRunLineState(
                "${runLabel.orEmpty()} · stale".trim(),
                null,
                ChatRunRight.Action(CHAT_REFRESH_HISTORY_LABEL, EntryUiEvent.RefreshSessionsClicked),
                active = false,
                error = true,
            )
        else -> null
    }

private fun OpenSessionUiState.historyLine(gateway: ChatRunRight.Gateway): ChatRunLineState =
    when {
        isCached && chatRefreshFailed() ->
            ChatRunLineState(
                "cached · unavailable",
                null,
                ChatRunRight.Action(CHAT_REFRESH_LABEL, EntryUiEvent.RefreshSessionsClicked),
                active = false,
                error = true,
            )
        isHistoryUnavailable() ->
            ChatRunLineState(
                "history · unavailable",
                null,
                ChatRunRight.Gateway(online = false),
                active = false,
                error = true,
            )
        isCached && isRefreshing ->
            ChatRunLineState("cached · refreshing", null, gateway, active = false, error = false)
        isRefreshing && chatMessageCount() == 0 ->
            ChatRunLineState("history · loading", null, gateway, active = false, error = false)
        else ->
            ChatRunLineState(
                "idle",
                "· ${chatMessageCount()} messages",
                gateway,
                active = false,
                error = false,
            )
    }

/** A Run owns the screen: its answer streams, or its state is still starting or completing. */
internal fun OpenSessionUiState.chatStreamingRun(): Boolean =
    activeResponse?.isStreaming == true ||
        latestRunState == RunPresentationState.RUNNING ||
        latestRunState == RunPresentationState.STARTING ||
        latestRunState == RunPresentationState.COMPLETING

/** What the screen shows may not match the Gateway, so the run line says `stale`. */
internal fun OpenSessionUiState.chatStaleRun(): Boolean =
    latestRun != null &&
        (
            isStale ||
                latestRunState == RunPresentationState.UNCERTAIN ||
                activeResponse?.streamInterrupted == true
        )

private fun OpenSessionUiState.activeRunLabel(): String? =
    activeResponse?.runId?.let(::chatRunLabel)
        ?: latestRun?.takeIf { chatStreamingRun() }?.let { run -> chatRunLabel(run.id) }

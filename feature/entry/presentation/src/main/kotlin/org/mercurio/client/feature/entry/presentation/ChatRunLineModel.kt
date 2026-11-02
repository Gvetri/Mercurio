package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState

/** The run line's right segment: the Gateway state, or the one offered action. */
internal sealed interface ChatRunRight {
    data class Gateway(
        val online: Boolean,
    ) : ChatRunRight

    data class Action(
        val label: UiText,
        val event: EntryUiEvent,
    ) : ChatRunRight
}

/** The run line under the transcript: state left, dim segment, one right segment. */
internal data class ChatRunLineState(
    val state: UiText,
    val dim: UiText?,
    val right: ChatRunRight,
    val active: Boolean,
    val error: Boolean,
)

/**
 * The run line for the current Session state. The state word is the one the design fixes:
 * `idle`, `sending`, `history · loading`, `cached · refreshing`, `cached · unavailable`,
 * `history · unavailable`, `reconciling run state`, and `run <id> · streaming|failed|stale`.
 * Stop lives here, so the composer action never shares its coordinate.
 */
internal fun chatRunLineState(detail: SessionDetailState): ChatRunLineState {
    val state = detail.content
    val runId = state.activeRunId() ?: state.latestRun?.id
    val gateway = ChatRunRight.Gateway(online = detail.chatGatewayOnline())
    return state.ownedRunLine(runId, gateway) ?: state.historyLine(gateway)
}

@Suppress("CyclomaticComplexMethod")
private fun OpenSessionUiState.ownedRunLine(
    runId: RunId?,
    gateway: ChatRunRight.Gateway,
): ChatRunLineState? =
    when {
        isReconciliationInProgress ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_reconciling),
                null,
                gateway,
                active = true,
                error = false,
            )
        chatStreamingRun() ->
            ChatRunLineState(
                state =
                    runState(
                        R.string.chat_run_state_streaming,
                        R.string.chat_run_state_streaming_unlabelled,
                        runId,
                    ),
                dim = null,
                right = ChatRunRight.Action(UiText.Resource(R.string.chat_stop), EntryUiEvent.StopRunClicked),
                active = true,
                error = false,
            )
        isSending ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_sending),
                uiText(R.string.chat_run_pending, chatPendingCount().coerceAtLeast(1)),
                gateway,
                active = true,
                error = false,
            )
        latestRunState == RunPresentationState.FAILED ->
            ChatRunLineState(
                runState(R.string.chat_run_state_failed, R.string.chat_run_state_failed_unlabelled, runId),
                null,
                gateway,
                active = false,
                error = true,
            )
        chatStaleRun() ->
            ChatRunLineState(
                runState(R.string.chat_run_state_stale, R.string.chat_run_state_stale_unlabelled, runId),
                null,
                ChatRunRight.Action(
                    UiText.Resource(R.string.chat_refresh_history),
                    EntryUiEvent.RefreshSessionsClicked,
                ),
                active = false,
                error = true,
            )
        else -> null
    }

private fun OpenSessionUiState.historyLine(gateway: ChatRunRight.Gateway): ChatRunLineState =
    when {
        isCached && chatRefreshFailed() ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_cached_unavailable),
                null,
                ChatRunRight.Action(
                    UiText.Resource(R.string.chat_refresh),
                    EntryUiEvent.RefreshSessionsClicked,
                ),
                active = false,
                error = true,
            )
        isHistoryUnavailable() ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_history_unavailable),
                null,
                ChatRunRight.Gateway(online = false),
                active = false,
                error = true,
            )
        isCached && isRefreshing ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_cached_refreshing),
                null,
                gateway,
                active = false,
                error = false,
            )
        isRefreshing && chatMessageCount() == 0 ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_history_loading),
                null,
                gateway,
                active = false,
                error = false,
            )
        else ->
            ChatRunLineState(
                UiText.Resource(R.string.chat_run_state_idle),
                uiText(R.string.chat_run_messages, chatMessageCount()),
                gateway,
                active = false,
                error = false,
            )
    }

private fun runState(
    labelledRes: Int,
    unlabelledRes: Int,
    runId: RunId?,
): UiText =
    if (runId == null) {
        UiText.Resource(unlabelledRes)
    } else {
        uiText(labelledRes, runId.value.take(CHAT_RUN_LABEL_LENGTH))
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

private fun OpenSessionUiState.activeRunId(): RunId? =
    activeResponse?.runId
        ?: latestRun?.takeIf { chatStreamingRun() }?.id

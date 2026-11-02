package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.isRunRetryEligible
import org.mercurio.client.feature.entry.domain.toRunPresentationState
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

enum class SessionListErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_UNAVAILABLE(R.string.error_session_list_unavailable),
    SESSION_UNAVAILABLE(R.string.error_session_unavailable),
}

enum class SessionCreationErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_session_not_created),
}

enum class SessionMutationAction {
    RENAME,
    PIN,
    UNPIN,
    DELETE,
}

enum class SessionMutationErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_session_action_failed),
}

enum class SessionRenameErrorCategory(
    val messageRes: Int,
) {
    EMPTY_TITLE(R.string.error_session_title_empty),
    CONTROL_CHARACTER(R.string.error_session_title_control_character),
    GATEWAY_REQUEST_FAILED(R.string.error_session_not_renamed),
}

enum class RelativeLastActiveUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

sealed interface RelativeLastActive {
    data object Unknown : RelativeLastActive

    data object Now : RelativeLastActive

    data class Ago(
        val amount: Long,
        val unit: RelativeLastActiveUnit,
    ) : RelativeLastActive
}

data class SessionItemUiState(
    val id: SessionId,
    val title: String,
    val preview: String?,
    val pinned: Boolean,
    val lastActive: RelativeLastActive = RelativeLastActive.Unknown,
    val isPendingTitle: Boolean = false,
)

data class SessionMessageUiState(
    val id: String,
    val role: String?,
    val content: String?,
    val runId: RunId? = null,
    val runStatus: String? = null,
    val runResult: String? = null,
    val timestamp: Instant? = null,
    val isStreaming: Boolean = false,
    val runState: RunPresentationState? = null,
    val streamInterrupted: Boolean = false,
    val failureMessage: UiText? = null,
    val failureTechnicalDetail: String? = null,
    val retryAvailable: Boolean = false,
) {
    val isFailedRun: Boolean
        get() =
            runState == RunPresentationState.FAILED ||
                runStatus?.toRunPresentationState() == RunPresentationState.FAILED
}

enum class SessionHistoryErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_history_refresh_failed),
    RECONCILIATION_FAILED(R.string.error_reconciliation_failed),
}

enum class MessageSendErrorCategory(
    val messageRes: Int,
) {
    GATEWAY_REQUEST_FAILED(R.string.error_message_not_sent),
    UNCERTAIN(R.string.error_message_uncertain),
    MODEL_REFUSED(R.string.error_message_model_refused),
    EFFORT_REFUSED(R.string.error_message_effort_refused),
}

enum class RunFailureCategory(
    val messageRes: Int,
) {
    GATEWAY_REPORTED(R.string.error_run_failed),
    MODEL_REFUSED(R.string.error_run_failed_model),
    EFFORT_REFUSED(R.string.error_run_failed_effort),
}

data class OpenSessionUiState(
    val session: SessionItemUiState,
    val messages: List<SessionMessageUiState>,
    val composerText: String = "",
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val errorCategory: SessionHistoryErrorCategory? = null,
    val latestRun: Run? = null,
    val activeRuns: List<Run> = emptyList(),
    val isSending: Boolean = false,
    val sendErrorCategory: MessageSendErrorCategory? = null,
    val hasUnresolvedSubmission: Boolean = false,
    val isDiscardUnresolvedSendConfirmationOpen: Boolean = false,
    val latestRunState: RunPresentationState? = null,
    val latestRunRetryAvailable: Boolean = false,
    val activeResponse: SessionMessageUiState? = null,
    val isReconciliationInProgress: Boolean = false,
    val composerFocusRequestId: Long = 0,
)

data class SessionCreationUiState(
    val isSubmitting: Boolean = false,
)

data class SessionRenameUiState(
    val titleDraft: String,
    val isSubmitting: Boolean = false,
    val errorCategory: SessionRenameErrorCategory? = null,
)

data class SessionDeleteUiState(
    val isSubmitting: Boolean = false,
)

data class SessionMutationUiState(
    val rename: SessionRenameUiState? = null,
    val delete: SessionDeleteUiState? = null,
    val pendingAction: SessionMutationAction? = null,
    val errorCategory: SessionMutationErrorCategory? = null,
    val retryAction: SessionMutationAction? = null,
)

data class SessionListUiState(
    val sessions: List<SessionItemUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val isUnavailable: Boolean = false,
    val errorCategory: SessionListErrorCategory? = null,
    val creationErrorCategory: SessionCreationErrorCategory? = null,
    val showFirstUseGuidance: Boolean = false,
    val openingSessionId: SessionId? = null,
    val openedSession: OpenSessionUiState? = null,
    val createSession: SessionCreationUiState? = null,
    val searchQuery: String = "",
    val nextOffset: Int? = null,
    val isLoadingMore: Boolean = false,
    val isSearching: Boolean = false,
    val sessionMutations: Map<SessionId, SessionMutationUiState> = emptyMap(),
    val modelPicker: ModelPickerUiState = ModelPickerUiState(),
) {
    val hasPendingMutation: Boolean
        get() = sessionMutations.values.any { it.pendingAction != null }

    /**
     * Search covers only server-provided Session titles and previews that this client
     * already loaded; the pinned `GET /api/sessions` has no general search parameter
     * (its `title` filter is an exact-title lookup) and transcripts are never fetched.
     */
    val visibleSessions: List<SessionItemUiState>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return sessions
            return sessions.filter { session ->
                session.title.contains(query, ignoreCase = true) ||
                    session.preview?.contains(query, ignoreCase = true) == true
            }
        }
}

internal val SessionListUiState.hasActiveRequest: Boolean
    get() =
        isLoading ||
            isRefreshing ||
            isLoadingMore ||
            isSearching ||
            openingSessionId != null ||
            createSession != null ||
            hasPendingMutation ||
            openedSession?.isSending == true ||
            openedSession?.isRefreshing == true

internal fun SessionListUiState.allowsSessionMutation(): Boolean =
    !hasActiveRequest &&
        !isUnavailable &&
        openedSession?.isRefreshing != true &&
        openedSession?.isReconciliationInProgress != true

internal fun SessionListUiState.showsNewSessionRowInEmptyState(): Boolean {
    return searchQuery.isBlank() && !isUnavailable && !isStale
}

internal fun SessionListUiState.createSessionActionEnabled(): Boolean =
    !isLoading &&
        !isRefreshing &&
        !isLoadingMore &&
        !isUnavailable &&
        openingSessionId == null &&
        createSession == null &&
        sessionMutations.isEmpty()

internal fun Session.toSessionItemUiState(now: Instant = Instant.now()): SessionItemUiState {
    val normalizedTitle = title?.trim().orEmpty()
    val pendingTitle = normalizedTitle.isEmpty()
    return SessionItemUiState(
        id = id,
        title = normalizedTitle,
        preview = preview?.takeIf(String::isNotBlank),
        pinned = pinned,
        lastActive = relativeLastActive(lastActive, now),
        isPendingTitle = pendingTitle,
    )
}

/**
 * Names the Gateway's last-active instant as the compact relative value the
 * Session list time column renders. A missing value keeps the unknown value.
 */
internal fun relativeLastActive(
    lastActive: Instant?,
    now: Instant,
): RelativeLastActive {
    if (lastActive == null) return RelativeLastActive.Unknown
    val seconds = Duration.between(lastActive, now).seconds
    return when {
        seconds < MINUTE_SECONDS -> RelativeLastActive.Now
        seconds < HOUR_SECONDS -> RelativeLastActive.Ago(seconds / MINUTE_SECONDS, RelativeLastActiveUnit.MINUTES)
        seconds < DAY_SECONDS -> RelativeLastActive.Ago(seconds / HOUR_SECONDS, RelativeLastActiveUnit.HOURS)
        seconds < WEEK_SECONDS -> RelativeLastActive.Ago(seconds / DAY_SECONDS, RelativeLastActiveUnit.DAYS)
        seconds < MONTH_SECONDS -> RelativeLastActive.Ago(seconds / WEEK_SECONDS, RelativeLastActiveUnit.WEEKS)
        seconds < YEAR_SECONDS ->
            RelativeLastActive.Ago(seconds / MONTH_SECONDS, RelativeLastActiveUnit.MONTHS)
        else -> RelativeLastActive.Ago(seconds / YEAR_SECONDS, RelativeLastActiveUnit.YEARS)
    }
}

private const val MINUTE_SECONDS = 60L
private const val HOUR_SECONDS = 3_600L
private const val DAY_SECONDS = 86_400L
private const val WEEK_SECONDS = 604_800L
private const val MONTH_SECONDS = 2_592_000L
private const val YEAR_SECONDS = 31_536_000L

internal fun GatewayHistoryMessage.toSessionMessageUiState(
    retryInput: String? = null,
    modelSelection: ModelSelection? = null,
): SessionMessageUiState {
    val failed = runStatus?.toRunPresentationState() == RunPresentationState.FAILED
    return SessionMessageUiState(
        id = id,
        role = role,
        content = content,
        runId = runId,
        runStatus = runStatus,
        runResult = runResult,
        timestamp = timestamp?.let(::parseGatewayTimestamp),
        failureMessage = if (failed) runFailureMessageFor(modelSelection) else null,
        retryAvailable = isRunRetryEligible(if (failed) RunPresentationState.FAILED else null, retryInput),
    )
}

private fun parseGatewayTimestamp(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull() ?: epochSecondsToInstant(value)

private const val NANOSECOND_SCALE = 9

private fun epochSecondsToInstant(value: String): Instant? {
    val seconds = value.toBigDecimalOrNull() ?: return null
    return runCatching {
        val wholeSeconds = seconds.toBigInteger()
        val fractionNanos = seconds.remainder(BigDecimal.ONE).movePointRight(NANOSECOND_SCALE).toLong()
        Instant.ofEpochSecond(wholeSeconds.longValueExact(), fractionNanos)
    }.getOrNull()
}

internal fun runFailureMessageFor(modelSelection: ModelSelection?): UiText {
    val category =
        when {
            modelSelection == null -> RunFailureCategory.GATEWAY_REPORTED
            modelSelection.effort != null -> RunFailureCategory.EFFORT_REFUSED
            else -> RunFailureCategory.MODEL_REFUSED
        }
    return UiText.Resource(category.messageRes)
}

internal fun List<SessionMessageUiState>.chronological(): List<SessionMessageUiState> {
    val timestamped =
        filter { it.timestamp != null }
            .sortedBy { requireNotNull(it.timestamp) }
    return when {
        timestamped.size == size -> timestamped
        timestamped.isEmpty() -> this
        else -> {
            var timestampedIndex = 0
            map { message ->
                if (message.timestamp == null) {
                    message
                } else {
                    timestamped[timestampedIndex++]
                }
            }
        }
    }
}

internal data class SessionDetailState(
    val content: OpenSessionUiState,
    val mutation: SessionMutationUiState?,
    val modelPicker: ModelPickerUiState = ModelPickerUiState(),
    val actionsEnabled: Boolean,
    val listRequestActive: Boolean,
    val listIsStale: Boolean,
    val listErrorCategory: SessionListErrorCategory?,
) {
    fun retryEnabled(canSubmit: Boolean): Boolean =
        actionsEnabled &&
            canSubmit &&
            !content.isRefreshing &&
            mutation?.pendingAction == null &&
            !content.isSending &&
            !content.isReconciliationInProgress &&
            !content.hasUnresolvedSubmission &&
            !listRequestActive
}

internal data class SessionRowUiState(
    val session: SessionItemUiState,
    val mutation: SessionMutationUiState?,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

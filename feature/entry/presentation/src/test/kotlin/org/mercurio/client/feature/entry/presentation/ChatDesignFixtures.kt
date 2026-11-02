package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunPresentationState
import org.mercurio.client.feature.entry.domain.SessionId
import java.time.Instant

/**
 * The Session states the approved Chat design draws, built from the same `SessionDetailState`
 * the production shell renders. Every value is the design page's own copy; a state that needs a
 * block renderer another issue owns is not built here.
 */
internal object ChatDesignFixtures {
    val sessionId = SessionId("nightly-release-triage")

    val runId = RunId("4b2a6c1e-9f31-4b8e-9c6d-000000000000")

    private val day = Instant.parse("2026-11-01T08:12:00Z")

    private val later = Instant.parse("2026-11-01T08:13:00Z")

    private fun session(): SessionItemUiState =
        SessionItemUiState(
            id = sessionId,
            title = "Nightly release triage",
            preview = "Two failing tests; retry green",
            pinned = true,
            lastActive = relativeLastActive("1h"),
        )

    private fun details(
        content: OpenSessionUiState,
        mutation: SessionMutationUiState? = null,
        actionsEnabled: Boolean = true,
        listIsStale: Boolean = false,
        listErrorCategory: SessionListErrorCategory? = null,
    ): SessionDetailState =
        SessionDetailState(
            content = content,
            mutation = mutation,
            modelPicker = ModelPickerUiState(),
            actionsEnabled = actionsEnabled,
            listRequestActive = false,
            listIsStale = listIsStale,
            listErrorCategory = listErrorCategory,
        )

    @Suppress("LongParameterList")
    private fun content(
        messages: List<SessionMessageUiState> = baseMessages(),
        composerText: String = "",
        isRefreshing: Boolean = false,
        isStale: Boolean = false,
        isCached: Boolean = false,
        errorCategory: SessionHistoryErrorCategory? = null,
        latestRun: Run? = null,
        latestRunState: RunPresentationState? = null,
        isSending: Boolean = false,
        activeResponse: SessionMessageUiState? = null,
        isReconciliationInProgress: Boolean = false,
    ): OpenSessionUiState =
        OpenSessionUiState(
            session = session(),
            messages = messages,
            composerText = composerText,
            isRefreshing = isRefreshing,
            isStale = isStale,
            isCached = isCached,
            errorCategory = errorCategory,
            latestRun = latestRun,
            latestRunState = latestRunState,
            isSending = isSending,
            activeResponse = activeResponse,
            isReconciliationInProgress = isReconciliationInProgress,
        )

    fun baseMessages(): List<SessionMessageUiState> =
        listOf(
            SessionMessageUiState(
                id = "message-1",
                role = "user",
                content = "Check the nightly release. `compose-jvm-tests` failed after the last merge.",
                timestamp = day,
            ),
            SessionMessageUiState(
                id = "message-2",
                role = "assistant",
                content =
                    "### What failed\n\n" +
                        "Two checks in compose-jvm-tests did not pass. The app still builds; " +
                        "the gate stops the release.\n\n" +
                        "- verifyRoborazziDebug — one Session list baseline differs.\n" +
                        "- check-design-values — two roles do not match the token package.",
                timestamp = day,
            ),
            SessionMessageUiState(
                id = "message-3",
                role = "assistant",
                content =
                    "```bash\n# gate output, run 4b2a\n./gradlew :app:composeJvmTest --no-daemon\n" +
                        "# 42 passed · 1 image differs\n```",
                timestamp = later,
            ),
        )

    fun empty(): SessionDetailState = details(content(messages = emptyList(), latestRun = null))

    fun loading(): SessionDetailState = details(content(messages = emptyList(), isRefreshing = true))

    fun cachedRefreshing(): SessionDetailState = details(content(isCached = true, isRefreshing = true))

    fun cachedUnavailable(): SessionDetailState =
        details(
            content(
                isCached = true,
                isStale = true,
                errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED,
            ),
        )

    fun unavailable(): SessionDetailState =
        details(
            content(
                messages = emptyList(),
                errorCategory = SessionHistoryErrorCategory.GATEWAY_REQUEST_FAILED,
            ),
            listErrorCategory = SessionListErrorCategory.GATEWAY_UNAVAILABLE,
        )

    fun populated(): SessionDetailState = details(content())

    fun sending(): SessionDetailState =
        details(
            content(
                messages =
                    baseMessages() +
                        SessionMessageUiState(
                            id = "local-user:${runId.value}",
                            role = "user",
                            content = "Re-record the baseline and re-run the gate.",
                            runId = runId,
                        ),
                isSending = true,
                latestRun = Run(runId, sessionId, "starting"),
                latestRunState = RunPresentationState.STARTING,
            ),
        )

    fun streaming(): SessionDetailState =
        details(
            content(
                latestRun = Run(runId, sessionId, "running"),
                latestRunState = RunPresentationState.RUNNING,
                activeResponse =
                    SessionMessageUiState(
                        id = "active-response",
                        role = "assistant",
                        content =
                            "The gate output will list each role and the delta. " +
                                "The run is still writing its answer",
                        runId = runId,
                        runState = RunPresentationState.RUNNING,
                        isStreaming = true,
                    ),
            ),
        )

    fun failed(): SessionDetailState =
        details(
            content(
                messages =
                    baseMessages() +
                        SessionMessageUiState(
                            id = "message-failed",
                            role = "assistant",
                            content = "",
                            runId = runId,
                            runState = RunPresentationState.FAILED,
                            failureMessage = UiText.Resource(R.string.error_run_failed),
                            failureTechnicalDetail = "gateway status 500 · run 4b2a",
                            retryAvailable = true,
                            timestamp = later,
                        ),
                latestRun = Run(runId, sessionId, "failed"),
                latestRunState = RunPresentationState.FAILED,
            ),
        )

    fun interrupted(): SessionDetailState =
        details(
            content(
                latestRun = Run(runId, sessionId, "interrupted"),
                latestRunState = RunPresentationState.UNCERTAIN,
                activeResponse =
                    SessionMessageUiState(
                        id = "active-response",
                        role = "assistant",
                        content =
                            "I stopped before the last table was written. The rows above are the " +
                                "part the Gateway confirmed.",
                        runId = runId,
                        streamInterrupted = true,
                    ),
            ),
        )

    fun stale(): SessionDetailState =
        details(
            content(
                latestRun = Run(runId, sessionId, "succeeded"),
                latestRunState = RunPresentationState.SUCCEEDED,
                isStale = true,
            ),
        )

    fun reconciling(): SessionDetailState =
        details(
            content(
                latestRun = Run(runId, sessionId, "running"),
                latestRunState = RunPresentationState.RUNNING,
                isRefreshing = true,
                isReconciliationInProgress = true,
            ),
        )

    fun draft(): SessionDetailState =
        details(
            content(
                composerText =
                    "re-record the list image\nagainst the approved hash\nre-run the value check\n" +
                        "attach both logs first\nkeep the last APK",
            ),
        )

    fun streamingWithDraft(): SessionDetailState =
        details(
            content(
                composerText = "Re-record the baseline",
                latestRun = Run(runId, sessionId, "running"),
                latestRunState = RunPresentationState.RUNNING,
                activeResponse =
                    SessionMessageUiState(
                        id = "active-response",
                        role = "assistant",
                        content = "Re-recording the Session list image against the approved hash.",
                        runId = runId,
                        runState = RunPresentationState.RUNNING,
                        isStreaming = true,
                    ),
            ),
        )

    fun staleWithDraft(): SessionDetailState =
        details(
            content(
                composerText = "Keep the partial answer",
                latestRun = Run(runId, sessionId, "interrupted"),
                latestRunState = RunPresentationState.UNCERTAIN,
                isStale = true,
            ),
        )

    fun longMessage(): SessionDetailState =
        details(
            content(
                messages =
                    listOf(
                        SessionMessageUiState(
                            id = "message-1",
                            role = "user",
                            content = "Write the release plan for the blocked nightly.",
                            timestamp = Instant.parse("2026-11-01T09:02:00Z"),
                        ),
                        SessionMessageUiState(
                            id = "message-2",
                            role = "assistant",
                            content = longReleasePlan(),
                            timestamp = Instant.parse("2026-11-01T09:03:00Z"),
                        ),
                    ),
            ),
        )

    private fun longReleasePlan(): String =
        buildString {
            appendLine("### Release plan")
            appendLine()
            appendLine("The nightly stays blocked until both failing checks pass. This plan keeps the run")
            appendLine("reproducible and the evidence on disk.")
            appendLine()
            repeat(5) { index ->
                appendLine("- Re-record the Session list image against the approved page hash, step ${index + 1}.")
            }
            appendLine()
            appendLine("A re-record is deliberate only when the design issue carries the hash. Otherwise it is")
            append("a regression with a new name.")
        }

    fun codeBlock(): SessionDetailState =
        details(
            content(
                messages =
                    listOf(
                        SessionMessageUiState(
                            id = "message-1",
                            role = "user",
                            content = "Show the exact gate command.",
                            timestamp = Instant.parse("2026-11-01T09:08:00Z"),
                        ),
                        SessionMessageUiState(
                            id = "message-2",
                            role = "assistant",
                            content = "Run it on the build host, not in CI. The value check reads the private package.",
                            timestamp = Instant.parse("2026-11-01T09:08:00Z"),
                        ),
                        SessionMessageUiState(
                            id = "message-3",
                            role = "assistant",
                            content =
                                "```bash\n# on local-server-2\npython3 scripts/check-design-values \\\n" +
                                    "  --package /srv/mercurio-design/mercurio \\\n" +
                                    "  --theme app/src/main/kotlin/Theme.kt\n" +
                                    "# exit 0 match · exit 1 mismatch · exit 2 unreadable\n```",
                            timestamp = Instant.parse("2026-11-01T09:08:00Z"),
                        ),
                    ),
            ),
        )
}

package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.mercurio.client.feature.entry.application.CreateSession
import org.mercurio.client.feature.entry.application.LoadSessionList
import org.mercurio.client.feature.entry.domain.GatewayException
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionPage

/**
 * Creates a Session from the `+ new session` row. The approved Session list design
 * has no title dialog: the request carries no title, the list stays in place, and
 * the new row renders the pending-title state until the Gateway titles it.
 */
internal fun EntryStateHolder.showCreateSession() {
    val gateway = sessionGateway ?: return
    val job =
        run {
            val current = mutableUiState.value.sessionList ?: return@run null
            if (current.blocksCreateSession()) return@run null
            val requestGeneration = beginSessionRequest()
            val request =
                CreateSessionRequest(
                    context = SessionRequestContext(requestGeneration, current.searchQuery, offset = null),
                    title = null,
                )
            mutableUiState.value =
                mutableUiState.value.copy(
                    sessionList =
                        current.copy(
                            createSession = SessionCreationUiState(isSubmitting = true),
                            creationErrorCategory = null,
                            errorCategory = null,
                            isLoading = false,
                            isRefreshing = false,
                            isSearching = false,
                            isLoadingMore = false,
                        ),
                )
            createSessionJob(gateway, request)
        } ?: return
    job.start()
}

internal fun EntryStateHolder.createSessionJob(
    gateway: SessionGatewayPort,
    request: CreateSessionRequest,
): Job =
    createSessionJob {
        try {
            val createdSession = CreateSession(gateway).execute(request.title)
            val refreshedPage = loadRefreshedPage(gateway)
            updateCurrentSessionRequest(request.context.generation) { current ->
                applyCreatedSession(current, createdSession, refreshedPage)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: GatewayException) {
            showCreateSessionFailure(request.context.generation)
        } catch (_: Exception) {
            showCreateSessionFailure(request.context.generation)
        }
    }

private fun EntryStateHolder.loadRefreshedPage(gateway: SessionGatewayPort): SessionPage? =
    try {
        LoadSessionList(gateway).execute(sessionListRequest(offset = null))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

private fun EntryStateHolder.applyCreatedSession(
    current: SessionListUiState,
    createdSession: Session,
    refreshedPage: SessionPage?,
): SessionListUiState =
    current.copy(
        sessions =
            refreshedPage?.let { page ->
                mergeSessions(
                    emptyList(),
                    page.sessions.map { it.toSessionItemUiState() },
                )
            } ?: mergeSession(current.sessions, createdSession),
        nextOffset = refreshedPage?.nextOffset,
        isLoading = false,
        isRefreshing = false,
        isSearching = false,
        isLoadingMore = false,
        isStale = refreshedPage == null,
        isCached = refreshedPage == null && current.isCached,
        isUnavailable = refreshedPage == null,
        errorCategory = if (refreshedPage == null) SessionListErrorCategory.GATEWAY_UNAVAILABLE else null,
        createSession = null,
        creationErrorCategory = null,
    )

internal suspend fun EntryStateHolder.showCreateSessionFailure(requestGeneration: Long) {
    updateCurrentSessionRequest(requestGeneration) { current ->
        current.copy(
            createSession = null,
            creationErrorCategory = SessionCreationErrorCategory.GATEWAY_REQUEST_FAILED,
        )
    }
}

internal fun EntryStateHolder.mergeSession(
    sessions: List<SessionItemUiState>,
    session: Session,
): List<SessionItemUiState> = mergeSessions(sessions, listOf(session.toSessionItemUiState()))

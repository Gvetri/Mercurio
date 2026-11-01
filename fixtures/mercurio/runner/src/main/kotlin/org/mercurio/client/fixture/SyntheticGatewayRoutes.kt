package org.mercurio.client.fixture

import com.sun.net.httpserver.HttpExchange
import java.net.URI

internal object SyntheticGatewayRoutes {
    internal fun handleSessionListRequest(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
    ) {
        val body = recordRequest(exchange, behavior)
        if (exchange.requestURI.path != "/api/sessions") {
            respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"not-found\"}")
            return
        }
        when (exchange.requestMethod) {
            "GET" -> handleSessionListRead(exchange, behavior)
            "POST" -> {
                val created =
                    SyntheticGatewaySession(
                        id = behavior.createdSessionId,
                        title = SyntheticGatewayJson.parseTitle(body),
                        preview = null,
                        pinned = false,
                        history =
                            listOf(
                                SyntheticGatewayMessage(
                                    id = "message-${behavior.createdSessionId}",
                                    role = "assistant",
                                    content = "Created history",
                                ),
                            ),
                    )
                behavior.sessions.removeIf { it.id == created.id }
                behavior.sessions += created
                respond(
                    exchange,
                    HTTP_CREATED,
                    "{\"object\":\"hermes.session\",\"session\":${SyntheticGatewayJson.sessionJson(created)}}",
                )
            }
            else -> respond(exchange, HTTP_METHOD_NOT_ALLOWED, "{\"error\":\"method-not-allowed\"}")
        }
    }

    private fun handleSessionListRead(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
    ) {
        if (behavior.failNextSessionList) {
            behavior.failNextSessionList = false
            respond(exchange, HTTP_SERVICE_UNAVAILABLE, "{\"error\":\"synthetic-refresh-failure\"}")
            return
        }
        val query = queryParameters(exchange.requestURI)
        val requestedLimit = query["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
        val offset = query["offset"]?.toIntOrNull() ?: 0
        val paginationFailure = sessionListPaginationFailure(requestedLimit, offset, behavior)
        if (paginationFailure != null) {
            respond(exchange, HTTP_BAD_REQUEST, paginationFailure)
            return
        }
        val effectiveLimit = minOf(requestedLimit, behavior.sessionPageSize ?: requestedLimit)
        val matchingSessions = behavior.sessions.toList()
        val page = matchingSessions.drop(offset).take(effectiveLimit)
        val hasMore = offset + page.size < matchingSessions.size
        val sessions = page.joinToString(",") { SyntheticGatewayJson.sessionJson(it) }
        respond(
            exchange,
            HTTP_OK,
            "{\"object\":\"list\",\"data\":[$sessions],\"limit\":$effectiveLimit,\"offset\":$offset," +
                "\"has_more\":$hasMore}",
        )
    }

    private fun sessionListPaginationFailure(
        requestedLimit: Int,
        offset: Int,
        behavior: SyntheticGatewayBehavior,
    ): String? =
        when {
            requestedLimit <= 0 || offset < 0 -> "{\"error\":\"invalid-pagination\"}"
            offset > behavior.sessions.size -> "{\"error\":\"offset-out-of-range\"}"
            else -> null
        }

    internal fun handleSessionResourceRequest(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
    ) {
        val body = recordRequest(exchange, behavior)
        val resource = exchange.requestURI.path.removePrefix("/api/sessions/")
        val segments = resource.split('/')
        val sessionId = segments.firstOrNull().orEmpty()
        val session = behavior.sessions.firstOrNull { it.id == sessionId }
        if (session == null || segments.size !in 1..2) {
            respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"session-not-found\"}")
            return
        }

        when (exchange.requestMethod) {
            "GET" ->
                when {
                    segments.size == 1 ->
                        respond(
                            exchange,
                            HTTP_OK,
                            "{\"object\":\"hermes.session\",\"session\":${SyntheticGatewayJson.sessionJson(session)}}",
                        )
                    segments[1] == "messages" -> respond(exchange, HTTP_OK, SyntheticGatewayJson.historyJson(session))
                    else -> respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"not-found\"}")
                }
            "PATCH" -> handleSessionPatch(exchange, behavior, session, body, segments)
            "DELETE" -> handleSessionDelete(exchange, behavior, sessionId, segments)
            else -> respond(exchange, HTTP_METHOD_NOT_ALLOWED, "{\"error\":\"method-not-allowed\"}")
        }
    }

    private fun handleSessionPatch(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
        session: SyntheticGatewaySession,
        body: String?,
        segments: List<String>,
    ) {
        if (segments.size != 1) {
            respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"not-found\"}")
        } else if (SyntheticGatewayJson.bodyContainsField(body, "pinned")) {
            applyPinnedPatch(exchange, behavior, session, body)
        } else {
            applyRenamePatch(exchange, behavior, session, body)
        }
    }

    private fun applyPinnedPatch(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
        session: SyntheticGatewaySession,
        body: String?,
    ) {
        val pinned = SyntheticGatewayJson.bodyBoolean(body, "pinned")
        when {
            pinned == null ->
                respond(exchange, HTTP_BAD_REQUEST, "{\"error\":\"invalid-session-field\"}")
            pinned && behavior.failNextSessionPin -> {
                behavior.failNextSessionPin = false
                respond(exchange, HTTP_SERVICE_UNAVAILABLE, "{\"error\":\"synthetic-pin-failure\"}")
            }
            !pinned && behavior.failNextSessionUnpin -> {
                behavior.failNextSessionUnpin = false
                respond(
                    exchange,
                    HTTP_SERVICE_UNAVAILABLE,
                    "{\"error\":\"synthetic-unpin-failure\"}",
                )
            }
            else -> {
                val updated = session.copy(pinned = pinned)
                replaceSession(behavior, updated)
                respond(
                    exchange,
                    HTTP_OK,
                    "{\"object\":\"hermes.session\",\"session\":${SyntheticGatewayJson.sessionJson(updated)}}",
                )
            }
        }
    }

    private fun applyRenamePatch(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
        session: SyntheticGatewaySession,
        body: String?,
    ) {
        if (behavior.failNextSessionRename) {
            behavior.failNextSessionRename = false
            respond(
                exchange,
                HTTP_SERVICE_UNAVAILABLE,
                "{\"error\":\"synthetic-rename-failure\"}",
            )
        } else {
            val renamed = session.copy(title = SyntheticGatewayJson.parseTitle(body))
            replaceSession(behavior, renamed)
            respond(
                exchange,
                HTTP_OK,
                "{\"object\":\"hermes.session\",\"session\":${SyntheticGatewayJson.sessionJson(renamed)}}",
            )
        }
    }

    private fun handleSessionDelete(
        exchange: HttpExchange,
        behavior: SyntheticGatewayBehavior,
        sessionId: String,
        segments: List<String>,
    ) {
        when {
            segments.size != 1 -> respond(exchange, HTTP_NOT_FOUND, "{\"error\":\"not-found\"}")
            behavior.failNextSessionDelete -> {
                behavior.failNextSessionDelete = false
                respond(exchange, HTTP_SERVICE_UNAVAILABLE, "{\"error\":\"synthetic-delete-failure\"}")
            }
            else -> {
                behavior.sessions.removeIf { it.id == sessionId }
                respond(
                    exchange,
                    HTTP_OK,
                    "{\"object\":\"hermes.session.deleted\",\"id\":${quote(sessionId)},\"deleted\":true}",
                )
            }
        }
    }
}

private const val HTTP_OK = 200

private const val HTTP_CREATED = 201

internal const val HTTP_BAD_REQUEST = 400

internal const val HTTP_METHOD_NOT_ALLOWED = 405

private const val HTTP_SERVICE_UNAVAILABLE = 503

private const val DEFAULT_PAGE_LIMIT = 50

private fun replaceSession(
    behavior: SyntheticGatewayBehavior,
    session: SyntheticGatewaySession,
) {
    val index = behavior.sessions.indexOfFirst { it.id == session.id }
    if (index >= 0) behavior.sessions[index] = session
}

internal fun queryParameters(uri: URI): Map<String, String> = GatewayHttpSupport.queryParameters(uri)

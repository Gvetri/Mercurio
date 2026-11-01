package org.mercurio.client.fixture.journey

import com.sun.net.httpserver.HttpExchange
import org.mercurio.client.fixture.GatewayHttpSupport

internal object JourneyGatewayRoutes {
    internal fun handleSessionList(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        val body = record(exchange, behavior)
        if (behavior.offlineGate.onSessionListRequest()) {
            respond(exchange, HTTP_SERVICE_UNAVAILABLE, """{"error":"synthetic-offline-failure"}""")
            return
        }
        if (unauthorized(exchange, behavior)) {
            respond(exchange, HTTP_UNAUTHORIZED, """{"error":"unauthorized"}""")
            return
        }
        when (exchange.requestMethod) {
            "GET" -> respondSessionListPage(exchange, behavior)
            "POST" -> {
                behavior.scenario.holdCreateMs?.let { holdMillis -> Thread.sleep(holdMillis.toLong()) }
                val created =
                    JourneySession(
                        id = behavior.createdSessionId(),
                        title = JourneyGatewayBodyJson.parseTitle(body),
                        preview = null,
                        pinned = false,
                        history = emptyList(),
                    )
                behavior.sessions.removeIf { it.id == created.id }
                behavior.sessions += created
                behavior.sessionMutations += "create" to created.id
                respond(
                    exchange,
                    HTTP_CREATED,
                    """{"object":"hermes.session","session":${JourneyGatewayJson.sessionJson(created)}}""",
                )
            }
            else -> respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")
        }
    }

    private fun respondSessionListPage(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        val query = GatewayHttpSupport.queryParameters(exchange.requestURI)
        val requestedLimit = query["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
        val offset = query["offset"]?.toIntOrNull() ?: 0
        val matching = behavior.sessions.toList()
        when {
            behavior.consumeFailNextSessionList() ->
                respond(exchange, HTTP_SERVICE_UNAVAILABLE, """{"error":"synthetic-refresh-failure"}""")
            requestedLimit <= 0 || offset < 0 ->
                respond(exchange, HTTP_BAD_REQUEST, """{"error":"invalid-pagination"}""")
            offset > matching.size ->
                respond(exchange, HTTP_BAD_REQUEST, """{"error":"offset-out-of-range"}""")
            else -> {
                val serverCap = behavior.scenario.sessionPageSize ?: requestedLimit
                val effectiveLimit = minOf(requestedLimit, serverCap)
                val page = matching.drop(offset).take(effectiveLimit)
                val hasMore = offset + page.size < matching.size
                val sessionsJson = page.joinToString(",") { JourneyGatewayJson.sessionJson(it) }
                respond(
                    exchange,
                    HTTP_OK,
                    """{"object":"list","data":[$sessionsJson],""" +
                        """"limit":$effectiveLimit,"offset":$offset,"has_more":$hasMore}""",
                )
            }
        }
    }

    internal fun handleSessionResource(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        val body = record(exchange, behavior)
        val offline = behavior.offlineGate.blocking
        if (offline || unauthorized(exchange, behavior)) {
            if (offline) {
                respond(exchange, HTTP_SERVICE_UNAVAILABLE, """{"error":"synthetic-offline-failure"}""")
            } else {
                respond(exchange, HTTP_UNAUTHORIZED, """{"error":"unauthorized"}""")
            }
            return
        }
        val resource = exchange.requestURI.path.removePrefix("/api/sessions/")
        val segments = resource.split('/')
        val sessionId = segments.firstOrNull().orEmpty()
        val session = behavior.sessions.firstOrNull { it.id == sessionId }
        if (session == null || segments.size !in 1..2) {
            respond(exchange, HTTP_NOT_FOUND, """{"error":"session-not-found"}""")
            return
        }
        when (exchange.requestMethod) {
            "GET" -> respondSessionResourceGet(exchange, behavior, session, segments)
            "PATCH" -> respondSessionResourcePatch(exchange, behavior, session, segments, body)
            "DELETE" -> respondSessionResourceDelete(exchange, behavior, session.id, segments)
            else -> respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")
        }
    }

    private fun respondSessionResourceGet(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        session: JourneySession,
        segments: List<String>,
    ) {
        when {
            segments.size == 1 ->
                respond(
                    exchange,
                    HTTP_OK,
                    """{"object":"hermes.session","session":${JourneyGatewayJson.sessionJson(session)}}""",
                )

            segments[1] == "messages" && behavior.offlineOpenGate.onSessionHistoryRequest() ->
                respond(exchange, HTTP_SERVICE_UNAVAILABLE, """{"error":"synthetic-offline-failure"}""")

            segments[1] == "messages" ->
                respond(exchange, HTTP_OK, JourneyGatewayJson.historyJson(behavior, session))

            else -> respond(exchange, HTTP_NOT_FOUND, """{"error":"not-found"}""")
        }
    }

    private fun respondSessionResourcePatch(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        session: JourneySession,
        segments: List<String>,
        body: String?,
    ) {
        if (segments.size != 1) {
            respond(exchange, HTTP_NOT_FOUND, """{"error":"not-found"}""")
            return
        }
        val pinned = JourneyGatewayBodyJson.parseBooleanField(body, "pinned")
        val updated =
            if (pinned != null) {
                session.copy(pinned = pinned)
            } else {
                session.copy(title = JourneyGatewayBodyJson.parseTitle(body))
            }
        behavior.replaceSession(updated)
        val operation =
            if (pinned == null) {
                "rename"
            } else if (pinned) {
                "pin"
            } else {
                "unpin"
            }
        behavior.sessionMutations += operation to session.id
        respond(
            exchange,
            HTTP_OK,
            """{"object":"hermes.session","session":${JourneyGatewayJson.sessionJson(updated)}}""",
        )
    }

    private fun respondSessionResourceDelete(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        sessionId: String,
        segments: List<String>,
    ) {
        if (segments.size != 1) {
            respond(exchange, HTTP_NOT_FOUND, """{"error":"not-found"}""")
            return
        }
        behavior.sessions.removeIf { it.id == sessionId }
        behavior.sessionMutations += "delete" to sessionId
        respond(
            exchange,
            HTTP_OK,
            """{"object":"hermes.session.deleted","id":${sessionId.jsonValue()},"deleted":true}""",
        )
    }

    internal fun handleModelCatalog(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        record(exchange, behavior)
        if (unauthorized(exchange, behavior)) {
            respond(exchange, HTTP_UNAUTHORIZED, """{"error":"unauthorized"}""")
            return
        }
        when {
            exchange.requestURI.path != "/api/models" ->
                respond(exchange, HTTP_NOT_FOUND, """{"error":"not-found"}""")

            exchange.requestMethod != "GET" ->
                respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")

            else -> respond(exchange, HTTP_OK, JourneyGatewayJson.catalogJson(behavior.scenario))
        }
    }

    internal fun handleRuns(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        val body = record(exchange, behavior)
        if (unauthorized(exchange, behavior)) {
            respond(exchange, HTTP_UNAUTHORIZED, """{"error":"unauthorized"}""")
            return
        }
        when {
            exchange.requestURI.path != "/v1/runs" -> handleRunResource(exchange, behavior)
            exchange.requestMethod != "POST" ->
                respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")
            else -> handleRunCreate(exchange, behavior, body)
        }
    }

    private fun handleRunCreate(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
        body: String?,
    ) {
        val sessionId = JourneyGatewayBodyJson.parseStringField(body, "session_id")
        if (sessionId == null || JourneyGatewayBodyJson.parseRunInput(body) == null) {
            respond(exchange, HTTP_BAD_REQUEST, """{"error":"invalid-run-request"}""")
            return
        }
        if (behavior.sessions.none { it.id == sessionId }) {
            respond(exchange, HTTP_NOT_FOUND, """{"error":"session-not-found"}""")
            return
        }
        val model = JourneyGatewayBodyJson.parseStringField(body, "model")
        val effort = JourneyGatewayBodyJson.parseStringField(body, "effort")
        val script = behavior.nextRunScript(sessionId)
        if (script == null) {
            behavior.recordSelection(sessionId, model, effort, accepted = false)
            respond(exchange, HTTP_NOT_FOUND, """{"error":"no-synthetic-run"}""")
        } else if (script.refuseCreate) {
            behavior.recordSelection(sessionId, model, effort, accepted = false)
            respond(exchange, HTTP_SERVICE_UNAVAILABLE, """{"error":"run-create-refused"}""")
        } else if (script.admissionRefusalCode != null) {
            behavior.recordSelection(sessionId, model, effort, accepted = false)
            respond(exchange, HTTP_BAD_REQUEST, JourneyGatewayJson.admissionRefusalJson(script))
        } else {
            behavior.recordSelection(sessionId, model, effort, accepted = true)
            respond(
                exchange,
                HTTP_ACCEPTED,
                """{"run_id":${script.runId.jsonValue()},"status":${script.createStatus.jsonValue()},""" +
                    """"replayed":false}""",
            )
        }
    }

    private fun handleRunResource(
        exchange: HttpExchange,
        behavior: JourneyGatewayBehavior,
    ) {
        val resource = exchange.requestURI.path.removePrefix("/v1/runs/")
        val segments = resource.split('/')
        val runId = segments.firstOrNull().orEmpty()
        val runResourceIsUnknown =
            runId.isEmpty() || segments.size !in 1..2 || (segments.size == 2 && segments[1] != "events")
        if (runResourceIsUnknown) {
            respond(exchange, HTTP_NOT_FOUND, """{"error":"run-not-found"}""")
            return
        }
        when (exchange.requestMethod) {
            "GET" -> {
                if (segments.size == 2) {
                    JourneyGatewaySse.streamRunEvents(exchange, behavior, runId)
                } else {
                    val status = behavior.runStatus(runId)
                    if (status == null) {
                        respond(exchange, HTTP_NOT_FOUND, """{"error":"run-not-found"}""")
                    } else {
                        behavior.runStatusRequests.incrementAndGet()
                        respond(
                            exchange,
                            HTTP_OK,
                            """{"object":"hermes.run","run_id":${runId.jsonValue()},"session_id":${behavior.sessionIdOf(
                                runId,
                            ).jsonValue()},"status":${status.jsonValue()},"updated_at":1757325600.0}""",
                        )
                    }
                }
            }
            else -> respond(exchange, HTTP_METHOD_NOT_ALLOWED, """{"error":"method-not-allowed"}""")
        }
    }
}

private const val HTTP_CREATED = 201

internal const val HTTP_ACCEPTED = 202
internal const val HTTP_OK = 200

internal const val HTTP_BAD_REQUEST = 400

internal const val HTTP_METHOD_NOT_ALLOWED = 405

private const val HTTP_SERVICE_UNAVAILABLE = 503

private const val DEFAULT_PAGE_LIMIT = 50

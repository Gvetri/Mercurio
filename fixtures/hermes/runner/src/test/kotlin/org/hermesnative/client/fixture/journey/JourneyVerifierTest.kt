package org.hermesnative.client.fixture.journey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

class JourneyVerifierTest {
    @Test
    fun verifier_accepts_declared_journeys_and_fails_for_undeclared_ones() {
        val process = startGateway("terminal-success", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/health")
            post(endpoint, "/v1/runs", """{"input":"Run this","session_id":"session-alpha"}""")
            streamEvents(endpoint, "run-success")
            get(endpoint, "/v1/runs/run-success")
            val telemetry = fetchTelemetry(endpoint.resolve("/__fixture/telemetry"))
            JourneyInvariants.verify("terminal-success", telemetry)
            val unknown =
                runCatching { JourneyInvariants.verify("not-a-journey", telemetry) }.exceptionOrNull()
            assertTrue(unknown is IllegalStateException)
        } finally {
            process.stop()
        }
    }

    @Test
    fun composer_stop_verifier_accepts_a_client_aborted_run_and_reconciled_status() {
        val process = startGateway("composer-stop", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/health")
            post(endpoint, "/v1/runs", """{"input":"Stop this run","session_id":"session-alpha"}""")
            abortStream(endpoint, "stop-run")
            awaitRunStatus(endpoint, "stop-run", "\"completed\"")
            get(endpoint, "/v1/runs/stop-run")
            val telemetry = fetchTelemetry(endpoint.resolve("/__fixture/telemetry"))
            JourneyInvariants.verify("composer-stop", telemetry)
        } finally {
            process.stop()
        }
    }

    @Test
    fun settings_verifier_requires_a_session_read_and_rejects_any_mutation() {
        val telemetry = settingsTelemetry(runCreates = mapOf("session-alpha" to 0, "session-beta" to 0))
        JourneyInvariants.verify("settings", telemetry)

        val runCreateError =
            runCatching {
                JourneyInvariants.verify(
                    "settings",
                    settingsTelemetry(runCreates = mapOf("session-alpha" to 1)),
                )
            }.exceptionOrNull()
        checkNotNull(runCreateError) { "The Settings verifier accepted a Run creation." }
        check(runCreateError.message!!.contains("recorded a Run creation")) {
            "Unexpected Settings verifier failure: ${runCreateError.message}"
        }

        val mutated =
            settingsTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                        "POST" to "/api/sessions",
                    ),
            )
        val error =
            runCatching { JourneyInvariants.verify("settings", mutated) }
                .exceptionOrNull()
        checkNotNull(error) { "The Settings verifier accepted a Session mutation." }
        check(error.message!!.contains("mutated Gateway state")) {
            "Unexpected Settings verifier failure: ${error.message}"
        }
    }

    @Test
    fun cache_lifecycle_verifiers_accept_get_only_telemetry_with_the_required_reads() {
        JourneyInvariants.verify(
            "settings-cache-clear",
            cacheLifecycleTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                        "GET" to "/api/sessions/session-alpha",
                    ),
            ),
        )
        JourneyInvariants.verify(
            "connection-removal-purge",
            cacheLifecycleTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                        "GET" to "/api/sessions",
                    ),
            ),
        )
        JourneyInvariants.verify(
            "cache-clear-repopulates",
            cacheLifecycleTelemetry(
                requests =
                    buildList {
                        add("GET" to "/v1/capabilities")
                        repeat(4) { add("GET" to "/api/sessions") }
                        repeat(4) { add("GET" to "/api/sessions/session-alpha") }
                    },
            ),
        )
    }

    @Test
    fun cache_lifecycle_verifiers_reject_gateway_writes_and_run_creations() {
        val mutated =
            cacheLifecycleTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                        "PATCH" to "/api/sessions/session-alpha",
                    ),
            )
        val writeError =
            runCatching { JourneyInvariants.verify("settings-cache-clear", mutated) }.exceptionOrNull()
        checkNotNull(writeError) { "The cache clear verifier accepted a Gateway mutation." }
        check(writeError.message!!.contains("mutated Gateway state")) {
            "Unexpected cache verifier failure: ${writeError.message}"
        }

        val runCreated =
            cacheLifecycleTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                    ),
                runCreates = mapOf("session-alpha" to 1),
            )
        val runError =
            runCatching { JourneyInvariants.verify("connection-removal-purge", runCreated) }.exceptionOrNull()
        checkNotNull(runError) { "The removal purge verifier accepted a Run creation." }
        check(runError.message!!.contains("recorded a Run creation")) {
            "Unexpected removal purge verifier failure: ${runError.message}"
        }
    }

    @Test
    fun cache_lifecycle_verifiers_require_their_distinctive_reads() {
        val noListRead =
            cacheLifecycleTelemetry(requests = listOf("GET" to "/v1/capabilities"))
        checkNotNull(
            runCatching { JourneyInvariants.verify("settings-cache-clear", noListRead) }.exceptionOrNull(),
        ) { "The cache clear verifier accepted a journey without a Session list read." }

        val singleListRead =
            cacheLifecycleTelemetry(
                requests =
                    listOf(
                        "GET" to "/v1/capabilities",
                        "GET" to "/api/sessions",
                    ),
            )
        val purgeError =
            runCatching { JourneyInvariants.verify("connection-removal-purge", singleListRead) }.exceptionOrNull()
        checkNotNull(purgeError) { "The removal purge verifier accepted a journey without a reconnect reload." }
        check(purgeError.message!!.contains("reloaded the Session list")) {
            "Unexpected removal purge verifier failure: ${purgeError.message}"
        }

        val repopulateError =
            runCatching { JourneyInvariants.verify("cache-clear-repopulates", singleListRead) }.exceptionOrNull()
        checkNotNull(repopulateError) {
            "The repopulation verifier accepted fewer reads than the offline arm scripts."
        }
        check(repopulateError.message!!.contains("retry the Session list")) {
            "Unexpected repopulation verifier failure: ${repopulateError.message}"
        }
    }

    private fun cacheLifecycleTelemetry(
        requests: List<Pair<String, String>>,
        runCreates: Map<String, Int> = emptyMap(),
    ): JsonObject =
        buildJsonObject {
            put(
                "requests",
                buildJsonArray {
                    requests.forEach { (method, path) ->
                        add(
                            buildJsonObject {
                                put("method", method)
                                put("path", path)
                            },
                        )
                    }
                },
            )
            if (runCreates.isNotEmpty()) {
                put(
                    "run_creates",
                    buildJsonObject {
                        runCreates.forEach { (session, count) -> put(session, count) }
                    },
                )
            }
        }

    private fun settingsTelemetry(
        runCreates: Map<String, Int> = emptyMap(),
        requests: List<Pair<String, String>> =
            listOf(
                "GET" to "/v1/capabilities",
                "GET" to "/api/sessions",
            ),
    ): JsonObject =
        buildJsonObject {
            put(
                "requests",
                buildJsonArray {
                    requests.forEach { (method, path) ->
                        add(
                            buildJsonObject {
                                put("method", method)
                                put("path", path)
                            },
                        )
                    }
                },
            )
            if (runCreates.isNotEmpty()) {
                put(
                    "run_creates",
                    buildJsonObject {
                        runCreates.forEach { (session, count) -> put(session, count) }
                    },
                )
            }
        }

    @Test
    fun every_committed_journey_scenario_declares_its_invariants() {
        val scenarioFiles =
            scenariosDir.listFiles().orEmpty().filter { file -> file.isFile && file.extension == "json" }
        val undeclared =
            scenarioFiles.map { file -> file.nameWithoutExtension }.filter { name ->
                val failure =
                    runCatching { JourneyInvariants.verify(name, JsonObject(emptyMap())) }.exceptionOrNull()
                failure is IllegalStateException &&
                    failure.message?.startsWith("No journey invariants declared") == true
            }
        assertTrue("No journey scenario JSONs found under $scenariosDir.", scenarioFiles.isNotEmpty())
        assertTrue("Journey scenarios without declared invariants: $undeclared", undeclared.isEmpty())
    }

    @Test
    fun telemetry_distinguishes_confirmed_session_operations_without_retaining_titles() {
        val process = startGateway("session-list-first", tls = false)
        try {
            val client = HttpClient.newHttpClient()
            listOf(
                """{"title":"do-not-export-title"}""",
                """{"pinned":true}""",
                """{"pinned":false}""",
            ).forEach { body ->
                val request =
                    HttpRequest.newBuilder(process.endpoint.resolve("/api/sessions/session-alpha"))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build()
                assertEquals(200, client.send(request, BodyHandlers.ofString()).statusCode())
            }
            val telemetry = fetchTelemetry(process.endpoint.resolve("/__fixture/telemetry"))
            assertFalse(telemetry.toString().contains("do-not-export-title"))
            assertEquals(
                listOf("rename", "pin", "unpin"),
                telemetry["session_mutations"]?.jsonArray.orEmpty()
                    .map { it.jsonObject.getValue("operation").jsonPrimitive.content },
            )
        } finally {
            process.stop()
        }
    }

    @Test
    fun lifecycle_verifier_rejects_extra_mutations_from_unconfirmed_actions() {
        val telemetry = lifecycleTelemetry(lifecycleRequests + ("DELETE" to "/api/sessions/session-alpha"))
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("session-lifecycle", telemetry)
        }
    }

    @Test
    fun lifecycle_verifier_accepts_only_the_complete_targeted_management_sequence() {
        JourneyInvariants.verify("session-lifecycle", lifecycleTelemetry(lifecycleRequests))
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("session-lifecycle", lifecycleTelemetry(lifecycleRequests.dropLast(1)))
        }
        val wrongTarget =
            lifecycleRequests.map {
                    (method, path) ->
                method to path.replace("synthetic-created-session-1", "session-alpha")
            }
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("session-lifecycle", lifecycleTelemetry(wrongTarget))
        }
    }

    @Test
    fun lifecycle_verifier_rejects_a_wrong_operation_with_the_same_http_method_and_path() {
        val wrongOperations = lifecycleOperations.toMutableList()
        wrongOperations[2] = "unpin" to "synthetic-created-session-1"
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("session-lifecycle", lifecycleTelemetry(operations = wrongOperations))
        }
    }

    @Test
    fun lifecycle_verifier_rejects_a_mutation_before_the_conversation_cancellation_checkpoint() {
        assertEarlyMutationRejected("synthetic-created-session-1")
    }

    @Test
    fun lifecycle_verifier_rejects_a_mutation_before_the_list_cancellation_checkpoint() {
        assertEarlyMutationRejected("session-alpha")
    }

    @Test
    fun empty_sessions_verifier_accepts_only_the_cancellation_checkpoint_order() {
        JourneyInvariants.verify("empty-sessions", emptySessionsTelemetry(emptySessionsRequests))
    }

    @Test
    fun empty_sessions_verifier_rejects_a_create_before_the_post_cancel_list_read() {
        val requests = emptySessionsRequests.toMutableList()
        val createIndex = requests.indexOf("POST" to "/api/sessions")
        val create = requests.removeAt(createIndex)
        requests.add(createIndex - 1, create)
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("empty-sessions", emptySessionsTelemetry(requests))
        }
    }

    @Test
    fun empty_sessions_verifier_rejects_a_missing_post_cancel_checkpoint_read() {
        val requests = emptySessionsRequests.toMutableList()
        val secondListRead =
            requests.withIndex().filter { it.value == "GET" to "/api/sessions" }.elementAt(1).index
        requests.removeAt(secondListRead)
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("empty-sessions", emptySessionsTelemetry(requests))
        }
    }

    @Test
    fun empty_sessions_verifier_rejects_a_missing_initial_list_read() {
        val requests = emptySessionsRequests.toMutableList()
        requests.removeAt(requests.indexOf("GET" to "/api/sessions"))
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("empty-sessions", emptySessionsTelemetry(requests))
        }
    }

    @Test
    fun empty_sessions_verifier_rejects_a_recorded_run_creation() {
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("empty-sessions", emptySessionsTelemetry(emptySessionsRequests, runCreates = true))
        }
    }

    @Test
    fun pending_creation_verifier_requires_exactly_one_authoritative_create_and_no_run() {
        JourneyInvariants.verify("pending-creation", pendingCreationTelemetry())
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify(
                "pending-creation",
                pendingCreationTelemetry(requests = pendingCreationRequests + ("POST" to "/api/sessions")),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify(
                "pending-creation",
                pendingCreationTelemetry(requests = pendingCreationRequests + ("POST" to "/v1/runs")),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("pending-creation", pendingCreationTelemetry(operations = emptyList()))
        }
        val dismissalRead =
            pendingCreationRequests.toMutableList().also { it.add(1, "GET" to "/api/sessions") }
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("pending-creation", pendingCreationTelemetry(requests = dismissalRead))
        }
    }

    @Test
    fun failed_offline_send_verifier_requires_two_run_creates_and_status_reconciliation() {
        JourneyInvariants.verify("failed-offline-send", offlineSendTelemetry())
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("failed-offline-send", offlineSendTelemetry(runCreates = 1))
        }
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("failed-offline-send", offlineSendTelemetry(runStatusRequests = 0))
        }
    }

    private fun offlineSendTelemetry(
        runCreates: Int = 2,
        runStatusRequests: Int = 1,
    ) = buildJsonObject {
        put(
            "requests",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("method", "GET")
                        put("path", "/v1/capabilities")
                    },
                )
            },
        )
        put(
            "run_creates",
            buildJsonObject {
                put("session-offline", runCreates)
            },
        )
        put("run_status_requests", runStatusRequests)
    }

    @Test
    fun verifier_trusts_the_committed_ca_when_fetching_https_telemetry() {
        val keystoreFile = File(repositoryRoot, "fixtures/hermes/journey-tls/journey-gateway.p12")
        val scenarioFile = File(scenariosDir, "terminal-success.json")
        val scenario =
            JourneyScenarioParser.parse(scenarioFile, pinnedDescriptor.provenance.value)
                .copy(port = freePort(), tls = true)
        val process = JourneyGatewayProcess.start(scenario, keystoreFile)
        try {
            val telemetry = fetchTelemetry(process.endpoint.resolve("/__fixture/telemetry"), keystoreFile)
            assertTrue(telemetry.toString().contains("terminal-success"))
        } finally {
            process.stop()
        }
    }

    private fun assertEarlyMutationRejected(sessionId: String) {
        val requests = lifecycleRequests.toMutableList()
        val firstMutation = requests.indexOfFirst { it == "PATCH" to "/api/sessions/$sessionId" }
        assertEquals("GET", requests[firstMutation - 1].first)
        val mutation = requests.removeAt(firstMutation)
        requests.add(firstMutation - 1, mutation)
        assertThrows(IllegalStateException::class.java) {
            JourneyInvariants.verify("session-lifecycle", lifecycleTelemetry(requests))
        }
    }

    private val lifecycleRequests =
        listOf(
            "GET" to "/api/sessions",
            "GET" to "/api/sessions",
            "GET" to "/api/sessions",
            "POST" to "/api/sessions",
            "GET" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions/synthetic-created-session-1/messages",
            "GET" to "/api/sessions",
            "GET" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions/synthetic-created-session-1/messages",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions",
            "PATCH" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions",
            "DELETE" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions",
            "PATCH" to "/api/sessions/session-alpha",
            "PATCH" to "/api/sessions/session-alpha",
            "GET" to "/api/sessions",
            "PATCH" to "/api/sessions/session-alpha",
            "GET" to "/api/sessions",
            "DELETE" to "/api/sessions/session-alpha",
        )

    private val lifecycleOperations =
        listOf(
            "create" to "synthetic-created-session-1",
            "rename" to "synthetic-created-session-1",
            "pin" to "synthetic-created-session-1",
            "unpin" to "synthetic-created-session-1",
            "delete" to "synthetic-created-session-1",
            "rename" to "session-alpha",
            "pin" to "session-alpha",
            "unpin" to "session-alpha",
            "delete" to "session-alpha",
        )

    private fun lifecycleTelemetry(
        requests: List<Pair<String, String>> = lifecycleRequests,
        operations: List<Pair<String, String>> = lifecycleOperations,
    ) = buildJsonObject {
        put(
            "session_mutations",
            buildJsonArray {
                operations.forEach { (operation, sessionId) ->
                    add(
                        buildJsonObject {
                            put("operation", operation)
                            put("session_id", sessionId)
                        },
                    )
                }
            },
        )
        put(
            "requests",
            buildJsonArray {
                (listOf("GET" to "/v1/capabilities") + requests).forEach { (method, path) ->
                    add(
                        buildJsonObject {
                            put("method", method)
                            put("path", path)
                        },
                    )
                }
            },
        )
    }

    private val emptySessionsRequests =
        listOf(
            "GET" to "/api/sessions",
            "GET" to "/api/sessions",
            "POST" to "/api/sessions",
            "GET" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions/synthetic-created-session-1/messages",
            "GET" to "/api/sessions",
        )

    private fun emptySessionsTelemetry(
        requests: List<Pair<String, String>> = emptySessionsRequests,
        runCreates: Boolean = false,
    ) = buildJsonObject {
        put(
            "session_mutations",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("operation", "create")
                        put("session_id", "synthetic-created-session-1")
                    },
                )
            },
        )
        put(
            "requests",
            buildJsonArray {
                (listOf("GET" to "/v1/capabilities") + requests).forEach { (method, path) ->
                    add(
                        buildJsonObject {
                            put("method", method)
                            put("path", path)
                        },
                    )
                }
            },
        )
        if (runCreates) {
            put(
                "run_creates",
                buildJsonObject {
                    put("synthetic-created-session-1", 1)
                },
            )
        }
    }

    private val pendingCreationRequests =
        listOf(
            "GET" to "/api/sessions",
            "POST" to "/api/sessions",
            "GET" to "/api/sessions/synthetic-created-session-1",
            "GET" to "/api/sessions/synthetic-created-session-1/messages",
            "GET" to "/api/sessions",
        )

    private fun pendingCreationTelemetry(
        requests: List<Pair<String, String>> = pendingCreationRequests,
        operations: List<Pair<String, String>> = listOf("create" to "synthetic-created-session-1"),
    ) = buildJsonObject {
        put(
            "session_mutations",
            buildJsonArray {
                operations.forEach { (operation, sessionId) ->
                    add(
                        buildJsonObject {
                            put("operation", operation)
                            put("session_id", sessionId)
                        },
                    )
                }
            },
        )
        put(
            "requests",
            buildJsonArray {
                (listOf("GET" to "/v1/capabilities") + requests).forEach { (method, path) ->
                    add(
                        buildJsonObject {
                            put("method", method)
                            put("path", path)
                        },
                    )
                }
            },
        )
        put("run_status_requests", 0)
        put("run_creates", buildJsonObject {})
        put("sse_connections", buildJsonObject {})
    }

    @Test
    fun picker_advertised_verifier_accepts_the_scripted_two_connection_flow_and_requires_both_fetches() {
        val process = startGateway("model-picker-advertised", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/v1/capabilities", bearer = "synthetic-token")
            get(endpoint, "/api/models", bearer = "synthetic-token")
            post(endpoint, "/v1/runs", pickerRunBody("hermes-agent", null), bearer = "synthetic-token")
            streamEvents(endpoint, "run-picker-1", bearer = "synthetic-token")
            get(endpoint, "/v1/runs/run-picker-1", bearer = "synthetic-token")
            get(endpoint, "/v1/capabilities", bearer = "synthetic-token")
            get(endpoint, "/api/models", bearer = "synthetic-token")
            post(endpoint, "/v1/runs", pickerRunBody("deepseek-v4-flash", "low"), bearer = "synthetic-token")
            streamEvents(endpoint, "run-picker-2", bearer = "synthetic-token")
            get(endpoint, "/v1/runs/run-picker-2", bearer = "synthetic-token")
            JourneyInvariants.verify(
                "model-picker-advertised",
                fetchTelemetry(endpoint.resolve("/__fixture/telemetry")),
            )

            val singleFetch = startGateway("model-picker-advertised", tls = false)
            try {
                val other = singleFetch.endpoint
                get(other, "/api/models", bearer = "synthetic-token")
                post(other, "/v1/runs", pickerRunBody("hermes-agent", null), bearer = "synthetic-token")
                streamEvents(other, "run-picker-1", bearer = "synthetic-token")
                get(other, "/v1/runs/run-picker-1", bearer = "synthetic-token")
                post(other, "/v1/runs", pickerRunBody("deepseek-v4-flash", "low"), bearer = "synthetic-token")
                streamEvents(other, "run-picker-2", bearer = "synthetic-token")
                get(other, "/v1/runs/run-picker-2", bearer = "synthetic-token")
                assertThrows(IllegalStateException::class.java) {
                    JourneyInvariants.verify(
                        "model-picker-advertised",
                        fetchTelemetry(other.resolve("/__fixture/telemetry")),
                    )
                }
            } finally {
                singleFetch.stop()
            }
        } finally {
            process.stop()
        }
    }

    @Test
    fun picker_absent_verifier_accepts_the_hidden_control_flow_and_rejects_any_catalog_read() {
        val process = startGateway("model-picker-absent", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/v1/capabilities")
            post(endpoint, "/v1/runs", pickerRunBody(null, null))
            streamEvents(endpoint, "run-absent")
            get(endpoint, "/v1/runs/run-absent")
            JourneyInvariants.verify("model-picker-absent", fetchTelemetry(endpoint.resolve("/__fixture/telemetry")))
            get(endpoint, "/api/models")
            assertThrows(IllegalStateException::class.java) {
                JourneyInvariants.verify(
                    "model-picker-absent",
                    fetchTelemetry(endpoint.resolve("/__fixture/telemetry")),
                )
            }
        } finally {
            process.stop()
        }
    }

    @Test
    fun picker_refusal_verifier_requires_the_refused_selection_before_the_accepted_retry() {
        val process = startGateway("model-picker-refusal", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/api/models")
            post(endpoint, "/v1/runs", pickerRunBody("deepseek-v4-flash", "high"))
            post(endpoint, "/v1/runs", pickerRunBody(null, null))
            streamEvents(endpoint, "run-refused-2")
            get(endpoint, "/v1/runs/run-refused-2")
            JourneyInvariants.verify("model-picker-refusal", fetchTelemetry(endpoint.resolve("/__fixture/telemetry")))

            val unselected = startGateway("model-picker-refusal", tls = false)
            try {
                val other = unselected.endpoint
                post(other, "/v1/runs", pickerRunBody(null, null))
                post(other, "/v1/runs", pickerRunBody(null, null))
                streamEvents(other, "run-refused-2")
                get(other, "/v1/runs/run-refused-2")
                assertThrows(IllegalStateException::class.java) {
                    JourneyInvariants.verify(
                        "model-picker-refusal",
                        fetchTelemetry(other.resolve("/__fixture/telemetry")),
                    )
                }
            } finally {
                unselected.stop()
            }
        } finally {
            process.stop()
        }
    }

    @Test
    fun mismatched_row_verifier_accepts_the_hidden_control_flow_and_rejects_a_catalog_read() {
        val process = startGateway("model-catalog-row-mismatch", tls = false)
        try {
            val endpoint = process.endpoint
            get(endpoint, "/v1/capabilities")
            post(endpoint, "/v1/runs", pickerRunBody(null, null))
            streamEvents(endpoint, "run-mismatch")
            get(endpoint, "/v1/runs/run-mismatch")
            JourneyInvariants.verify(
                "model-catalog-row-mismatch",
                fetchTelemetry(endpoint.resolve("/__fixture/telemetry")),
            )
            get(endpoint, "/api/models")
            assertThrows(IllegalStateException::class.java) {
                JourneyInvariants.verify(
                    "model-catalog-row-mismatch",
                    fetchTelemetry(endpoint.resolve("/__fixture/telemetry")),
                )
            }
        } finally {
            process.stop()
        }
    }

    private fun pickerRunBody(
        model: String?,
        effort: String?,
    ): String {
        val selection =
            when {
                model == null -> ""
                effort == null -> ""","model":"$model""""
                else -> ""","model":"$model","effort":"$effort""""
            }
        return """{"input":"Picker journey input","session_id":"session-alpha"$selection}"""
    }
}

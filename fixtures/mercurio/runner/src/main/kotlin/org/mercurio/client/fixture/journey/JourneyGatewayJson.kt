package org.mercurio.client.fixture.journey

import org.mercurio.client.fixture.GatewayHttpSupport

internal object JourneyGatewayJson {
    internal fun capabilitiesJson(scenario: JourneyScenario): String =
        buildString {
            append("""{"object":"hermes.api_server.capabilities","platform":"hermes-agent",""")
            append(""""model":"synthetic","auth":{"type":"bearer","required":""")
            append(scenario.requireBearerCredential != null)
            append(
                "},\"features\":{\"run_submission\":true,\"run_status\":true," +
                    "\"run_events_sse\":true,\"session_resources\":true},\"endpoints\":{",
            )
            append(
                scenario.capabilities.joinToString(",") { name ->
                    val endpoint = cataloguedEndpoint(scenario, name)
                    val method = GatewayHttpSupport.quote(endpoint.method)
                    val path = GatewayHttpSupport.quote(endpoint.path)
                    """${GatewayHttpSupport.quote(
                        name,
                    )}:{"method":$method,"path":$path}"""
                },
            )
            append("}}")
        }

    private fun cataloguedEndpoint(
        scenario: JourneyScenario,
        name: String,
    ): JourneyEndpointCatalog.Endpoint =
        if (name == "run_models" && scenario.modelCatalogRow != null) {
            JourneyEndpointCatalog.Endpoint(scenario.modelCatalogRow.method, scenario.modelCatalogRow.path)
        } else {
            JourneyEndpointCatalog.endpoints.getValue(name)
        }

    internal fun catalogJson(scenario: JourneyScenario): String {
        val catalog = requireNotNull(scenario.modelCatalog) { "The scenario declares no model catalog." }
        val models = catalog.entries.joinToString(",") { entry -> catalogEntryJson(entry) }
        return "{\"object\":\"hermes.api_server.run_models\",\"default_model\":" +
            catalog.defaultModel.jsonValue() + ",\"models\":[" + models + "]}"
    }

    private fun catalogEntryJson(entry: JourneyModelCatalogEntry): String {
        val effort =
            if (entry.supportedEfforts.isEmpty()) {
                ""
            } else {
                val efforts = entry.supportedEfforts.joinToString(",") { GatewayHttpSupport.quote(it) }
                ",\"effort\":{\"supported\":[" + efforts + "],\"default\":" +
                    entry.defaultEffort.jsonValue() + "}"
            }
        return "{\"id\":" + entry.id.jsonValue() + ",\"display_name\":" + entry.displayName.jsonValue() +
            ",\"provider\":" + entry.provider.jsonValue() + effort + "}"
    }

    internal fun admissionRefusalJson(script: JourneyRunScript): String {
        val code = requireNotNull(script.admissionRefusalCode).jsonValue()
        return "{\"error\":{\"message\":\"synthetic-admission-refusal\"," +
            "\"type\":\"invalid_request_error\",\"param\":null,\"code\":$code}}"
    }

    internal fun renderEvent(
        script: JourneyRunScript,
        event: JourneyRunEvent,
        eventIndex: Int,
    ): ByteArray {
        val data =
            buildString {
                append("""{"event":${event.type.jsonValue()},"run_id":${script.runId.jsonValue()}""")
                event.delta?.let { append(""","delta":${it.jsonValue()}""") }
                append('}')
            }
        val record =
            buildString {
                if (script.liveFraming) {
                    append("id: $eventIndex\n")
                }
                append("data: $data\n\n")
            }
        return record.toByteArray(Charsets.UTF_8)
    }

    internal fun sessionJson(session: JourneySession): String =
        buildString {
            append(
                """{"id":${session.id.jsonValue()},"source":"api_server","title":${session.title.jsonValue()},""",
            )
            append(""""preview":${session.preview.jsonValue()},"pinned":${session.pinned},""")
            append(""""message_count":${session.history.size}}""")
        }

    internal fun historyJson(
        behavior: JourneyGatewayBehavior,
        session: JourneySession,
    ): String {
        val messages = behavior.historyMessagesFor(session)
        return buildString {
            append("""{"object":"list","session_id":${session.id.jsonValue()},"data":[""")
            append(messages.joinToString(",") { messageJson(it) })
            append("""],"pagination":{"limit":500,"offset":0,"order":"latest","returned":${messages.size}}}""")
        }
    }

    private fun messageJson(message: JourneyMessage): String =
        buildString {
            append(
                """{"id":${message.id.jsonValue()},"role":${message.role.jsonValue()},""" +
                    """"content":${message.content.jsonValue()}""",
            )
            message.timestamp?.let { append(""","timestamp":${it.jsonValue()}""") }
            append('}')
        }
}

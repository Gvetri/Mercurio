package org.mercurio.client.fixture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object SyntheticGatewayJson {
    internal fun sessionJson(session: SyntheticGatewaySession): String =
        buildString {
            append("{\"id\":")
            append(quote(session.id))
            append(",\"source\":\"api_server\",\"title\":")
            append(session.title.jsonValue())
            append(",\"preview\":")
            append(session.preview.jsonValue())
            append(",\"pinned\":")
            append(session.pinned)
            append(",\"message_count\":")
            append(session.history.size)
            append('}')
        }

    internal fun historyJson(session: SyntheticGatewaySession): String =
        buildString {
            append("{\"object\":\"list\",\"session_id\":")
            append(quote(session.id))
            append(",\"data\":[")
            append(session.history.joinToString(",") { messageJson(it) })
            append("],\"pagination\":{\"limit\":500,\"offset\":0,\"order\":\"latest\",\"returned\":")
            append(session.history.size)
            append("}}")
        }

    private fun messageJson(message: SyntheticGatewayMessage): String =
        buildString {
            append("{\"id\":")
            append(quote(message.id))
            append(",\"role\":")
            append(message.role.jsonValue())
            append(",\"content\":")
            append(message.content.jsonValue())
            message.timestamp?.let { append(",\"timestamp\":${quote(it)}") }
            append('}')
        }

    internal fun bodyContainsField(
        body: String?,
        field: String,
    ): Boolean {
        val text = body?.takeIf(String::isNotBlank) ?: return false
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        return root?.containsKey(field) ?: false
    }

    internal fun bodyBoolean(
        body: String?,
        field: String,
    ): Boolean? {
        val root =
            body?.takeIf(String::isNotBlank)
                ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        val value = root?.get(field) as? JsonPrimitive ?: return null
        return if (value.isString || value.content !in setOf("true", "false")) {
            null
        } else {
            value.content == "true"
        }
    }

    private fun String?.jsonValue(): String = GatewayHttpSupport.jsonValue(this)

    internal fun parseTitle(body: String?): String? {
        val root =
            body?.takeIf(String::isNotBlank)
                ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return null
        val title = root["title"]
        return if (title == null || title == JsonNull) null else (title as? JsonPrimitive)?.content
    }
}

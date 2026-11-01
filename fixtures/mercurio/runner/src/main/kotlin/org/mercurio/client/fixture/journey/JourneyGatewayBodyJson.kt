package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.Json

internal object JourneyGatewayBodyJson {
    internal fun parseTitle(body: String?): String? = parseStringField(body, "title")

    internal fun parseStringField(
        body: String?,
        field: String,
    ): String? {
        val root =
            body?.takeIf(String::isNotBlank)
                ?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() }
                as? kotlinx.serialization.json.JsonObject
                ?: return null
        val value = root[field]
        return if (value == null || value == kotlinx.serialization.json.JsonNull) {
            null
        } else {
            (value as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        }
    }

    internal fun parseBooleanField(
        body: String?,
        field: String,
    ): Boolean? {
        val root =
            body?.takeIf(String::isNotBlank)
                ?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() }
                as? kotlinx.serialization.json.JsonObject
        val value = root?.get(field) as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return if (value.isString || value.content !in setOf("true", "false")) {
            null
        } else {
            value.content == "true"
        }
    }

    internal fun parseRunInput(body: String?): String? {
        val root =
            body?.takeIf(String::isNotBlank)
                ?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() }
                as? kotlinx.serialization.json.JsonObject
                ?: return null
        return (root["input"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

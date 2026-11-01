package org.mercurio.client.fixture.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class RequiredJsonFields(
    private val sourceName: String,
    private val root: JsonObject,
) {
    fun requiredString(path: String): String {
        val value = required(path)
        if (value !is JsonPrimitive || !value.isString) {
            invalidType(path, "string")
        }
        return value.content
    }

    fun requiredNullableString(path: String): String? {
        val value = required(path)
        if (value == JsonNull) return null
        return requiredString(path)
    }

    fun requiredInt(path: String): Int {
        val value = required(path)
        if (value !is JsonPrimitive || value.isString) invalidType(path, "integer")
        return value.content.toIntOrNull() ?: invalidType(path, "integer")
    }

    fun requiredBoolean(path: String): Boolean {
        val value = required(path)
        if (value !is JsonPrimitive || value.isString || value.content !in setOf("true", "false")) {
            invalidType(path, "boolean")
        }
        return value.content == "true"
    }

    fun requiredArray(path: String): JsonArray {
        val value = required(path)
        if (value !is JsonArray) invalidType(path, "array")
        return value
    }

    fun requiredObject(path: String): JsonObject {
        val value = required(path)
        if (value !is JsonObject) invalidType(path, "object")
        return value
    }

    fun requiredStringArray(path: String): List<String> =
        requiredArray(path).mapIndexed { index, value ->
            if (value !is JsonPrimitive || !value.isString) {
                invalidType("$path[$index]", "string")
            }
            value.content
        }

    private fun required(path: String): JsonElement {
        var current: JsonElement = root
        path.split('.').forEach { segment ->
            val jsonObject = current as? JsonObject
            if (jsonObject == null) invalidType(path, "object containing '$segment'")
            current = jsonObject[segment] ?: missing(path)
        }
        return current
    }

    private fun missing(path: String): Nothing =
        throw ContractFixtureException(
            ContractFixtureFailureCategory.MISSING_REQUIRED_FIELD,
            "$sourceName is missing required field '$path'.",
        )

    private fun invalidType(
        path: String,
        expected: String,
    ): Nothing =
        throw ContractFixtureException(
            ContractFixtureFailureCategory.INVALID_REQUIRED_FIELD_TYPE,
            "$sourceName field '$path' must be an $expected.",
        )
}

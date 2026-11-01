package org.mercurio.client.fixture.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class ContractJsonFixture(
    val sourceName: String,
    val provenanceField: String,
    val provenanceValue: String,
    val root: JsonObject,
) {
    fun requiredString(path: String): String = fields().requiredString(path)

    fun requiredNullableString(path: String): String? = fields().requiredNullableString(path)

    fun requiredInt(path: String): Int = fields().requiredInt(path)

    fun requiredBoolean(path: String): Boolean = fields().requiredBoolean(path)

    fun requiredArray(path: String): JsonArray = fields().requiredArray(path)

    fun requiredObject(path: String): JsonObject = fields().requiredObject(path)

    fun requiredStringArray(path: String): List<String> = fields().requiredStringArray(path)

    private fun fields(): RequiredJsonFields = RequiredJsonFields(sourceName, root)
}

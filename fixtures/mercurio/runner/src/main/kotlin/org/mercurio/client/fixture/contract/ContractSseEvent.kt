package org.mercurio.client.fixture.contract

import kotlinx.serialization.json.JsonObject

data class ContractSseEvent(
    val eventType: String,
    val data: JsonObject,
) {
    fun requiredString(path: String): String = fields().requiredString(path)

    fun requiredInt(path: String): Int = fields().requiredInt(path)

    private fun fields(): RequiredJsonFields = RequiredJsonFields("SSE event '$eventType'", data)
}

package org.mercurio.client.fixture.journey

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object JourneyScenarioCatalog {
    internal fun parseModelCatalog(root: JsonObject): JourneyModelCatalog? {
        val catalog = root["model_catalog"] ?: return null
        val catalogObject =
            catalog as? JsonObject
                ?: throw JourneyScenarioFormatException("The model catalog must be a JSON object.")
        rejectUnknownKeys(catalogObject, allowedCatalogKeys, "model_catalog")
        val models =
            catalogObject["models"]?.jsonArray
                ?: throw JourneyScenarioFormatException("The model catalog must declare a models list.")
        return JourneyModelCatalog(
            defaultModel = nullableRequiredString(catalogObject, "default_model"),
            entries =
                models.mapIndexed { index, element ->
                    parseModelCatalogEntry(element.jsonObject, index)
                },
        )
    }

    private fun parseModelCatalogEntry(
        entry: JsonObject,
        index: Int,
    ): JourneyModelCatalogEntry {
        rejectUnknownKeys(entry, allowedCatalogEntryKeys, "model_catalog.models[$index]")
        val effort = entry["effort"]?.jsonObject
        effort?.let { rejectUnknownKeys(it, allowedEffortKeys, "model_catalog.models[$index].effort") }
        if (effort != null && effort["supported"] == null) {
            throw JourneyScenarioFormatException("model_catalog.models[$index].effort must declare supported efforts.")
        }
        return JourneyModelCatalogEntry(
            id = JourneyScenarioJson.requiredString(entry, "id"),
            displayName = JourneyScenarioJson.requiredString(entry, "display_name"),
            provider = JourneyScenarioJson.optionalString(entry, "provider"),
            supportedEfforts = effort?.let { JourneyScenarioJson.requiredStringList(it, "supported") }.orEmpty(),
            defaultEffort = effort?.let { JourneyScenarioJson.optionalString(it, "default") },
        )
    }

    internal fun parseModelCatalogRow(root: JsonObject): JourneyModelCatalogRow? {
        val row = root["model_catalog_row"] ?: return null
        val rowObject =
            row as? JsonObject
                ?: throw JourneyScenarioFormatException("The model catalog row override must be a JSON object.")
        rejectUnknownKeys(rowObject, allowedCatalogRowKeys, "model_catalog_row")
        return JourneyModelCatalogRow(
            method = JourneyScenarioJson.requiredString(rowObject, "method"),
            path = JourneyScenarioJson.requiredString(rowObject, "path"),
        )
    }

    internal fun nullableRequiredString(
        container: JsonObject,
        key: String,
    ): String? {
        val value =
            container[key]
                ?: throw JourneyScenarioFormatException("Field '$key' is required and may be null.")
        return if (value == JsonNull) {
            null
        } else {
            (value as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
                ?: throw JourneyScenarioFormatException("Field '$key' must be a non-blank string or null.")
        }
    }
}

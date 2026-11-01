package org.mercurio.client.fixture.contract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.mercurio.client.fixture.PinnedFixtureDescriptor

object ContractFixtureParser {
    private val json = Json
    private val provenanceFields = setOf("hermes_revision", "image_digest")

    fun parseJson(
        sourceName: String,
        content: String,
        descriptor: PinnedFixtureDescriptor,
    ): ContractJsonFixture {
        val root =
            try {
                json.parseToJsonElement(content) as? JsonObject
                    ?: fail(
                        ContractFixtureFailureCategory.INVALID_JSON,
                        "$sourceName must contain a JSON object.",
                    )
            } catch (error: ContractFixtureException) {
                throw error
            } catch (error: SerializationException) {
                throw ContractFixtureException(
                    ContractFixtureFailureCategory.INVALID_JSON,
                    "$sourceName is not valid JSON.",
                    error,
                )
            }
        val provenance = validateProvenance(sourceName, content, root, descriptor)
        return ContractJsonFixture(
            sourceName = sourceName,
            provenanceField = descriptor.provenanceField,
            provenanceValue = provenance,
            root = root,
        )
    }

    fun parseSse(
        sourceName: String,
        content: String,
        descriptor: PinnedFixtureDescriptor,
    ): ContractSseFixture {
        val normalized = content.replace("\r\n", "\n")
        if (!normalized.endsWith("\n")) {
            fail(
                ContractFixtureFailureCategory.INVALID_SSE_FRAMING,
                "$sourceName must end with a newline after the final event.",
            )
        }
        val eventContent = if (normalized.endsWith("\n\n")) normalized.dropLast(2) else normalized.dropLast(1)
        val records = eventContent.split("\n\n")
        if (records.isEmpty() || records.any(String::isBlank)) {
            fail(
                ContractFixtureFailureCategory.INVALID_SSE_FRAMING,
                "$sourceName contains an empty SSE event record.",
            )
        }

        val events = records.map { record -> parseEvent(sourceName, record) }
        val provenanceData =
            events.mapNotNull { event ->
                event.data.takeIf { it.containsKey(descriptor.provenanceField) }
            }.singleOrNull()
        val provenance =
            validateProvenance(
                sourceName = sourceName,
                content = content,
                root = provenanceData,
                descriptor = descriptor,
            )
        return ContractSseFixture(
            sourceName = sourceName,
            provenanceField = descriptor.provenanceField,
            provenanceValue = provenance,
            events = events,
        )
    }

    private fun parseEvent(
        sourceName: String,
        record: String,
    ): ContractSseEvent {
        val lines = record.split('\n')
        val dataLines = lines.filter { it.startsWith("data:") }
        if (dataLines.isEmpty() || dataLines.size != lines.size) {
            fail(
                ContractFixtureFailureCategory.INVALID_SSE_FRAMING,
                "$sourceName contains an SSE record with invalid data framing.",
            )
        }
        val dataContent = dataLines.joinToString("\n") { it.removePrefix("data:").trimStart() }
        val data =
            try {
                json.parseToJsonElement(dataContent) as? JsonObject
                    ?: fail(
                        ContractFixtureFailureCategory.INVALID_REQUIRED_FIELD_TYPE,
                        "$sourceName SSE payload must contain a JSON object.",
                    )
            } catch (error: ContractFixtureException) {
                throw error
            } catch (error: SerializationException) {
                throw ContractFixtureException(
                    ContractFixtureFailureCategory.INVALID_JSON,
                    "$sourceName SSE payload is not valid JSON.",
                    error,
                )
            }
        val eventType =
            (data["event"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)
                ?: fail(
                    ContractFixtureFailureCategory.INVALID_SSE_FRAMING,
                    "$sourceName contains an SSE event without an event type.",
                )
        return ContractSseEvent(
            eventType = eventType,
            data = data,
        )
    }

    private fun validateProvenance(
        sourceName: String,
        content: String,
        root: JsonObject?,
        descriptor: PinnedFixtureDescriptor,
    ): String {
        val selectedCount = countJsonMember(content, descriptor.provenanceField)
        val alternateField = provenanceFields.first { it != descriptor.provenanceField }
        val alternateCount = countJsonMember(content, alternateField)
        if (selectedCount == 0 && alternateCount == 0) {
            fail(
                ContractFixtureFailureCategory.MISSING_PROVENANCE,
                "$sourceName must contain ${descriptor.provenanceField} provenance.",
            )
        }
        if (selectedCount == 0 && alternateCount > 0) {
            fail(
                ContractFixtureFailureCategory.INVALID_PROVENANCE,
                "$sourceName must use the pinned ${descriptor.provenanceField} provenance field.",
            )
        }
        if (selectedCount != 1 || alternateCount != 0) {
            fail(
                ContractFixtureFailureCategory.DUPLICATE_PROVENANCE,
                "$sourceName must contain exactly one ${descriptor.provenanceField} provenance field.",
            )
        }
        val value = root?.get(descriptor.provenanceField)
        if (value !is JsonPrimitive || !value.isString) {
            fail(
                ContractFixtureFailureCategory.INVALID_REQUIRED_FIELD_TYPE,
                "$sourceName provenance must be a JSON string.",
            )
        }
        if (value.content != descriptor.provenanceValue) {
            fail(
                ContractFixtureFailureCategory.INVALID_PROVENANCE,
                "$sourceName provenance does not match the pinned descriptor.",
            )
        }
        return value.content
    }

    internal fun countJsonMember(
        content: String,
        field: String,
    ): Int {
        var objectDepth = 0
        var index = 0
        var count = 0
        while (index < content.length) {
            when (content[index]) {
                '"' -> {
                    val end = jsonStringEnd(content, index)
                    count += countMemberAt(content, index, end, objectDepth, field)
                    index = end
                }
                '{' -> {
                    objectDepth++
                    index++
                }
                '}' -> {
                    objectDepth = (objectDepth - 1).coerceAtLeast(0)
                    index++
                }
                else -> index++
            }
        }
        return count
    }

    private fun countMemberAt(
        content: String,
        start: Int,
        end: Int,
        objectDepth: Int,
        field: String,
    ): Int {
        val next = skipWhitespace(content, end)
        val isMemberCandidate = objectDepth == 1 && next < content.length && content[next] == ':'
        if (!isMemberCandidate) return 0
        val memberName =
            runCatching {
                (json.parseToJsonElement(content.substring(start, end)) as JsonPrimitive).content
            }.getOrNull()
        return if (memberName == field) 1 else 0
    }

    private fun jsonStringEnd(
        content: String,
        start: Int,
    ): Int {
        var index = start + 1
        var escaped = false
        while (index < content.length) {
            when {
                escaped -> escaped = false
                content[index] == '\\' -> escaped = true
                content[index] == '"' -> return index + 1
            }
            index++
        }
        return content.length
    }

    private fun skipWhitespace(
        content: String,
        start: Int,
    ): Int {
        var index = start
        while (index < content.length && content[index].isWhitespace()) index++
        return index
    }

    private fun fail(
        category: ContractFixtureFailureCategory,
        message: String,
    ): Nothing = throw ContractFixtureException(category, message)
}

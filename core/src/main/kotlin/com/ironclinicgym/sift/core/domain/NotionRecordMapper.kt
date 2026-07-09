package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns a raw Notion page object into a [SiftTask], reading values **through the mapping**.
 *
 * Properties on a Notion page are keyed by name, but we locate each one by its stable
 * property id (matching the binding's id), so a user renaming a property does not break Sift
 * and no property name is ever hardcoded. Pure Kotlin over kotlinx.serialization JSON.
 */
object NotionRecordMapper {

    fun toTask(page: JsonObject, mapping: DatabaseMapping): SiftTask {
        val pageId = page["id"]?.jsonPrimitive?.content.orEmpty()
        val props = page["properties"] as? JsonObject ?: JsonObject(emptyMap())

        fun valueFor(role: Role): JsonObject? {
            val propertyId = mapping.propertyFor(role)?.propertyId ?: return null
            return props.values.firstOrNull { v ->
                (v as? JsonObject)?.get("id")?.jsonPrimitive?.content == propertyId
            }?.jsonObject
        }

        val titleProp = valueFor(Role.TITLE)
        val priorityProp = valueFor(Role.PRIORITY)
        val bucketProp = valueFor(Role.BUCKET)
        val statusProp = valueFor(Role.STATUS)
        val dueProp = valueFor(Role.DUE_DATE)
        val notesProp = valueFor(Role.NOTES)
        val recurrenceRuleProp = valueFor(Role.RECURRENCE_RULE)
        val recurrenceDisplayProp = valueFor(Role.RECURRENCE_DISPLAY)
        val labelsProp = valueFor(Role.LABELS)

        val (priorityId, priorityName) = readSelect(priorityProp)
        val (bucketId, bucketName) = readSelect(bucketProp)

        return SiftTask(
            pageId = pageId,
            mappingId = mapping.id,
            title = readTitle(titleProp),
            priorityOptionId = priorityId,
            priorityOptionName = priorityName,
            bucketOptionId = bucketId,
            bucketOptionName = bucketName,
            isDone = readDone(statusProp),
            due = dueProp?.get("date")?.jsonObject?.get("start")?.jsonPrimitive?.content,
            notes = readRichText(notesProp),
            recurrenceRule = readRichText(recurrenceRuleProp),
            recurrenceDisplay = readRichText(recurrenceDisplayProp),
            labels = readLabels(labelsProp),
        )
    }

    private fun readTitle(prop: JsonObject?): String =
        (prop?.get("title") as? kotlinx.serialization.json.JsonArray)
            ?.joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
            .orEmpty()

    private fun readRichText(prop: JsonObject?): String? =
        (prop?.get("rich_text") as? kotlinx.serialization.json.JsonArray)
            ?.joinToString("") { it.jsonObject["plain_text"]?.jsonPrimitive?.content.orEmpty() }
            ?.ifBlank { null }

    /** select or status both expose {id, name}. Returns (id, name). */
    private fun readSelect(prop: JsonObject?): Pair<String?, String?> {
        val inner = (prop?.get("select") ?: prop?.get("status")) as? JsonObject ?: return null to null
        return inner["id"]?.jsonPrimitive?.content to inner["name"]?.jsonPrimitive?.content
    }

    /** select → single label, multi_select → list of labels. */
    private fun readLabels(prop: JsonObject?): List<String> {
        val multiSelect = prop?.get("multi_select") as? kotlinx.serialization.json.JsonArray
        if (multiSelect != null) {
            return multiSelect.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        }
        val (_, name) = readSelect(prop)
        return if (name != null) listOf(name) else emptyList()
    }

    /** A checkbox is done when true; a status counts as done if it is in a complete group. */
    private fun readDone(prop: JsonObject?): Boolean {
        prop?.get("checkbox")?.jsonPrimitive?.booleanOrNull?.let { return it }
        // A page's status value carries a name but not its group, so "done" is inferred from
        // the option name. Phase 2 can refine this using the status property's group metadata.
        val statusName = (prop?.get("status") as? JsonObject)?.get("name")?.jsonPrimitive?.content
        return statusName?.lowercase()?.let { it.contains("done") || it.contains("complete") } ?: false
    }
}

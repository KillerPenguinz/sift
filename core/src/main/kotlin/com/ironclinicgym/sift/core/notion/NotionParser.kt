package com.ironclinicgym.sift.core.notion

import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.notion.model.NotionDataSourceRef
import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionDatabaseSummary
import com.ironclinicgym.sift.core.notion.model.NotionPageSummary
import com.ironclinicgym.sift.core.notion.model.NotionPropertySchema
import com.ironclinicgym.sift.core.notion.model.NotionSelectOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Notion's dynamic record/schema JSON into Sift's plain result models.
 *
 * Property *names* read here are the user's own names from their schema; they are never
 * hardcoded against. They flow straight into the mapping as data. The opinionated names this
 * project authors live only in SchemaTemplate (automatic setup creation), and are mapped immediately.
 */
object NotionParser {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** A database object (from search or retrieve), including its data sources. */
    fun parseDatabaseSummary(obj: JsonObject): NotionDatabaseSummary {
        val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
        val title = readPlainText(obj["title"]).ifBlank { "Untitled database" }
        val dataSources = (obj["data_sources"] as? JsonArray).orEmptyArray().map { ds ->
            val o = ds.jsonObject
            NotionDataSourceRef(
                id = o["id"]?.jsonPrimitive?.content.orEmpty(),
                name = readPlainText(o["name"]).ifBlank { title },
            )
        }
        return NotionDatabaseSummary(databaseId = id, title = title, dataSources = dataSources)
    }

    /** A data source object (from search or retrieve), including its property schema. */
    fun parseDataSourceSchema(obj: JsonObject): NotionDataSourceSchema {
        val dataSourceId = obj["id"]?.jsonPrimitive?.content.orEmpty()
        // Parent shape varies; the database id may live under parent.database_id or database_id.
        val databaseId = (obj["parent"] as? JsonObject)?.get("database_id")?.jsonPrimitive?.content
            ?: obj["database_id"]?.jsonPrimitive?.content
            ?: ""
        val title = readPlainText(obj["name"]).ifBlank { readPlainText(obj["title"]) }
            .ifBlank { "Untitled" }
        val props = (obj["properties"] as? JsonObject).orEmptyObject().map { (propName, value) ->
            parseProperty(propName, value.jsonObject)
        }
        return NotionDataSourceSchema(databaseId, dataSourceId, title, props)
    }

    fun parsePageId(obj: JsonObject): String = obj["id"]?.jsonPrimitive?.content.orEmpty()

    /** A page object from search: id, its title-property text, and its parent kind. */
    fun parsePageSummary(obj: JsonObject): NotionPageSummary {
        val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
        // Parent kind distinguishes real pages (workspace / page_id) from database rows
        // (database_id / data_source_id). Default to workspace when absent.
        val parentType = (obj["parent"] as? JsonObject)?.get("type")?.jsonPrimitive?.content ?: "workspace"
        val properties = obj["properties"] as? JsonObject
        val titleText = properties?.values
            ?.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "title" }
            ?.let { readPlainText((it as JsonObject)["title"]) }
            .orEmpty()
        return NotionPageSummary(id, titleText.ifBlank { "Untitled page" }, parentType)
    }

    /** Extract a plain-text line from a block, or null for blocks with no text (dividers, media). */
    fun parseBlockText(block: JsonObject): String? {
        val type = block["type"]?.jsonPrimitive?.content ?: return null
        val content = block[type] as? JsonObject ?: return null
        val text = readPlainText(content["rich_text"])
        if (text.isBlank()) return null
        return when (type) {
            "bulleted_list_item", "numbered_list_item" -> "•  $text"
            "to_do" -> {
                val checked = content["checked"]?.jsonPrimitive?.content == "true"
                (if (checked) "☑  " else "☐  ") + text
            }
            "quote" -> "“$text”"
            else -> text
        }
    }

    private fun parseProperty(name: String, value: JsonObject): NotionPropertySchema {
        val id = value["id"]?.jsonPrimitive?.content.orEmpty()
        val typeKey = value["type"]?.jsonPrimitive?.content.orEmpty()
        val type = PropertyType.fromApi(typeKey)
        val options = if (type.hasOptions) {
            val container = value[typeKey] as? JsonObject
            (container?.get("options") as? JsonArray).orEmptyArray().map { opt ->
                val o = opt.jsonObject
                NotionSelectOption(
                    id = o["id"]?.jsonPrimitive?.content,
                    name = o["name"]?.jsonPrimitive?.content.orEmpty(),
                    color = o["color"]?.jsonPrimitive?.content,
                )
            }
        } else {
            emptyList()
        }
        return NotionPropertySchema(id = id, name = name, type = type, options = options)
    }

    /** Read Notion rich-text/plain-string fields into a flat string. */
    private fun readPlainText(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonArray -> element.joinToString("") { item ->
            (item as? JsonObject)?.get("plain_text")?.jsonPrimitive?.content
                ?: (item as? JsonObject)?.get("text")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: ""
        }
        is JsonObject -> element["content"]?.jsonPrimitive?.content.orEmpty()
        else -> element.jsonPrimitive.content
    }

    private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
    private fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())
}

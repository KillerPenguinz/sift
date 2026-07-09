package com.ironclinicgym.sift.core.notion.model

import com.ironclinicgym.sift.core.mapping.PropertyType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Notion wire models.
 *
 * The list/token envelopes are `@Serializable` because their shape is stable. The actual
 * record + schema bodies are deeply dynamic (property values vary by type), so they are
 * carried as [JsonObject] and turned into the plain result models below by NotionParser.
 * No platform types; serialization only.
 */

/** Stable envelope for any Notion list endpoint (search, query). */
@Serializable
data class NotionListEnvelope(
    val results: List<JsonObject> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** OAuth token-exchange response. workspace fields avoid needing the user-info capability. */
@Serializable
data class NotionTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("bot_id") val botId: String? = null,
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("workspace_name") val workspaceName: String? = null,
    @SerialName("workspace_icon") val workspaceIcon: String? = null,
    @SerialName("duplicated_template_id") val duplicatedTemplateId: String? = null,
)

// ---- Plain parsed result models (consumed by the mapping/domain layers) ----

/** A select / status / multi-select option as seen in a property schema. */
data class NotionSelectOption(val id: String?, val name: String, val color: String?)

/** One property in a data source schema: its id, display name, type, and any options. */
data class NotionPropertySchema(
    val id: String,
    val name: String,
    val type: PropertyType,
    val options: List<NotionSelectOption> = emptyList(),
)

/** A data source's full schema, resolved to its parent database. */
data class NotionDataSourceSchema(
    val databaseId: String,
    val dataSourceId: String,
    val title: String,
    val properties: List<NotionPropertySchema>,
) {
    fun property(role: PropertyType) = properties.firstOrNull { it.type == role }
}

/** A data source within a database, as listed when retrieving a database. */
data class NotionDataSourceRef(val id: String, val name: String)

/** A database the connection can see, with its data sources. */
data class NotionDatabaseSummary(
    val databaseId: String,
    val title: String,
    val dataSources: List<NotionDataSourceRef>,
)

/** Minimal result of creating/retrieving a page (seed rows, write-back confirmation). */
data class NotionPageResult(val pageId: String)

/**
 * A page the connection can see, used to resolve where automatic setup creates its database.
 * [parentType] is the Notion parent kind ("workspace", "page_id", "database_id",
 * "data_source_id"); database *rows* are page objects too, so automatic setup filters to
 * real pages (workspace / page_id) and drops rows.
 */
data class NotionPageSummary(val pageId: String, val title: String, val parentType: String = "workspace")

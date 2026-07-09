package com.ironclinicgym.sift.core.mapping

/**
 * The Notion property types Sift understands, plus an [UNSUPPORTED] fallback for anything
 * we don't model. [apiType] is the exact string Notion uses in its schema JSON, so this is
 * the only place that vocabulary lives. Pure Kotlin; no platform types.
 */
enum class PropertyType(val apiType: String) {
    TITLE("title"),
    RICH_TEXT("rich_text"),
    NUMBER("number"),
    SELECT("select"),
    MULTI_SELECT("multi_select"),
    STATUS("status"),
    DATE("date"),
    CHECKBOX("checkbox"),
    URL("url"),
    EMAIL("email"),
    PHONE("phone_number"),
    PEOPLE("people"),
    FILES("files"),
    FORMULA("formula"),
    RELATION("relation"),
    ROLLUP("rollup"),
    CREATED_TIME("created_time"),
    LAST_EDITED_TIME("last_edited_time"),
    UNIQUE_ID("unique_id"),
    UNSUPPORTED("");

    /** Property types that carry a fixed set of named options (drive priorities / buckets). */
    val hasOptions: Boolean get() = this == SELECT || this == MULTI_SELECT || this == STATUS

    companion object {
        fun fromApi(apiType: String): PropertyType =
            entries.firstOrNull { it.apiType == apiType } ?: UNSUPPORTED
    }
}

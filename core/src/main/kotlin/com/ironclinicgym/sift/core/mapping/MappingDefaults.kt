package com.ironclinicgym.sift.core.mapping

import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema

/**
 * Small mapping-time helpers shared by both onboarding doors.
 *
 * Pure Kotlin, no platform types. These resolve a couple of facts from a data source schema
 * that the write layer needs later (for example which status option means "done"), so the
 * runtime write path never has to re-fetch the schema or guess by a hardcoded name.
 */
object MappingDefaults {

    /**
     * For a status role, decide how "complete" is expressed:
     *  - a checkbox status has no option, so this returns null and complete writes `true`;
     *  - a status-typed property returns the [DoneStatus] whose option looks complete, so
     *    complete sets that option by name.
     *
     * The heuristic mirrors [NotionRecordMapper.readDone]: an option whose name contains
     * "done" or "complete". Falls back to the last option (Notion convention places the
     * complete group last). Returns null if the property has no options.
     */
    fun resolveDoneStatus(schema: NotionDataSourceSchema, statusBinding: RolePropertyBinding?): DoneStatus? {
        if (statusBinding == null || statusBinding.propertyType != PropertyType.STATUS) return null
        val prop = schema.properties.firstOrNull { it.id == statusBinding.propertyId } ?: return null
        if (prop.options.isEmpty()) return null
        val match = prop.options.firstOrNull { opt ->
            opt.name.lowercase().let { it.contains("done") || it.contains("complete") }
        } ?: prop.options.last()
        return DoneStatus(optionId = match.id ?: match.name, optionName = match.name)
    }
}

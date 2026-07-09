package com.ironclinicgym.sift.core.mapping

import kotlinx.serialization.Serializable

/**
 * The mapping layer — the spine of Sift.
 *
 * Every read and write in every later phase goes through a [DatabaseMapping]: it binds each
 * semantic [Role] to the user's actual property identity, and resolves to a specific **data
 * bucket within a database** ([DataSourceRef]), not merely a database id. No property name is
 * ever hardcoded; callers ask the mapping for the property bound to a role.
 *
 * The top-level [MappingSet] holds a *list* of mappings so app-side multi-database
 * consolidation can be added later without rearchitecting. v1 stores exactly one entry.
 *
 * All types are `@Serializable` so the mapping can be persisted to secure storage by the
 * platform layer with no Android dependency leaking into core. Option keys (priority/bucket)
 * are stored as plain strings (the enum name, or null when unmatched) to keep this module
 * decoupled from the theme enums while still allowing color/icon reuse.
 */

/** Resolves to a specific data source inside a database, scoped to a workspace. */
@Serializable
data class DataSourceRef(
    val workspaceId: String,
    val databaseId: String,
    val dataSourceId: String,
)

/** Binds one [Role] to the user's actual property (by stable id, with name + type for UI). */
@Serializable
data class RolePropertyBinding(
    val role: Role,
    val propertyId: String,
    val propertyName: String,
    val propertyType: PropertyType,
)

/**
 * One option of the priority property, promoted to a board priority.
 * [priorityKey] is the matched default priority name (e.g. "ASAP") for color/order reuse, or
 * null when the user's option did not match a default. The option is a priority regardless.
 */
@Serializable
data class PriorityBinding(
    val optionId: String,
    val optionName: String,
    val order: Int,
    val priorityKey: String? = null,
    val colorHint: String? = null,
)

/**
 * Identifies which option of a *status*-typed status property means "done", captured at
 * mapping time so [complete][com.ironclinicgym.sift.core.domain.TaskWriteService.complete]
 * can set the right option by name without ever hardcoding it. Null when the status role is a
 * checkbox (complete is simply `checkbox = true`) — see [DatabaseMapping.doneStatus].
 */
@Serializable
data class DoneStatus(val optionId: String, val optionName: String)

/** One option of the bucket property, with optional default-bucket association for color. */
@Serializable
data class BucketBinding(
    val optionId: String,
    val optionName: String,
    val bucketKey: String? = null,
    val colorHint: String? = null,
)

/** A complete mapping for one data source. */
@Serializable
data class DatabaseMapping(
    val id: String,
    val ref: DataSourceRef,
    val label: String,
    val bindings: List<RolePropertyBinding>,
    val priorities: List<PriorityBinding>,
    val buckets: List<BucketBinding>,
    /**
     * For a status-typed status role, which option counts as done. Null for a checkbox status
     * (complete = `true`). Resolved from the schema at mapping time (see MappingDefaults).
     */
    val doneStatus: DoneStatus? = null,
) {
    /** The property bound to [role], or null if unbound (optional roles may be unbound). */
    fun propertyFor(role: Role): RolePropertyBinding? = bindings.firstOrNull { it.role == role }

    /** True once both Sift recurrence fields are bound (recurrence available for this database). */
    val hasRecurrenceFields: Boolean
        get() = propertyFor(Role.RECURRENCE_RULE) != null && propertyFor(Role.RECURRENCE_DISPLAY) != null

    /** Throws if a required role is missing — call only after validation has passed. */
    fun requireProperty(role: Role): RolePropertyBinding =
        propertyFor(role) ?: error("Mapping ${id} has no property bound to role $role")
}

/**
 * The persisted set of mappings. [activeId] selects the board v1 reads from.
 * Holding a list (not a single mapping) is a hard requirement so multi-database
 * consolidation is additive later.
 */
@Serializable
data class MappingSet(
    val mappings: List<DatabaseMapping> = emptyList(),
    val activeId: String? = null,
) {
    val active: DatabaseMapping?
        get() = mappings.firstOrNull { it.id == activeId } ?: mappings.firstOrNull()

    fun withMapping(mapping: DatabaseMapping, makeActive: Boolean = true): MappingSet {
        val replaced = mappings.filterNot { it.id == mapping.id } + mapping
        return copy(mappings = replaced, activeId = if (makeActive) mapping.id else activeId)
    }

    companion object {
        val EMPTY = MappingSet()
    }
}

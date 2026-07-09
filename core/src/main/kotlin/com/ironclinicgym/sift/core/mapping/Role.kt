package com.ironclinicgym.sift.core.mapping

/**
 * The semantic roles Sift needs from a Notion data source. A [Role] is *what Sift means*;
 * the user's actual property name is bound to it in the mapping. Nothing in the app ever
 * reads a property by hardcoded name — it asks the mapping for the property bound to a role.
 *
 * Four required roles drive the board; three optional roles enrich it.
 */
enum class Role(val required: Boolean) {
    /** The priority property. Its options become the urgency priorities. */
    PRIORITY(true),

    /** The identity property: color + icon grouping (Work, Personal, …). */
    BUCKET(false),

    /** Done / status. */
    STATUS(true),

    /** The page title. */
    TITLE(true),

    DUE_DATE(false),
    NOTES(false),

    /** A flag used later for time gating of work tasks. */
    WORK_FLAG(false),

    /**
     * Sift-managed recurrence fields (Phase 3). Optional and only bound once the two
     * Sift-prefixed columns exist: [RECURRENCE_RULE] holds the technical RRULE string,
     * [RECURRENCE_DISPLAY] the plain-English label Sift generates. Both are rich text so
     * they read cleanly in Notion. Bound by property id like every other role.
     */
    RECURRENCE_RULE(false),
    RECURRENCE_DISPLAY(false),

    /** User labels (tags, categories) from a select or multi-select property. */
    LABELS(false);

    companion object {
        val required: List<Role> get() = entries.filter { it.required }
        val optional: List<Role> get() = entries.filter { !it.required }
    }
}

/** Which [PropertyType]s are acceptable for a given [Role]. Drives mapping validation. */
data class RoleRequirement(val role: Role, val allowedTypes: Set<PropertyType>)

/**
 * The compatibility contract. Kept conservative and explained in plain language to the user
 * when a chosen property does not fit (see MappingValidator).
 */
val ROLE_REQUIREMENTS: Map<Role, RoleRequirement> = linkedMapOf(
    Role.PRIORITY to RoleRequirement(Role.PRIORITY, setOf(PropertyType.SELECT, PropertyType.STATUS)),
    Role.BUCKET to RoleRequirement(Role.BUCKET, setOf(PropertyType.SELECT, PropertyType.MULTI_SELECT, PropertyType.STATUS)),
    Role.STATUS to RoleRequirement(Role.STATUS, setOf(PropertyType.STATUS, PropertyType.CHECKBOX)),
    Role.TITLE to RoleRequirement(Role.TITLE, setOf(PropertyType.TITLE)),
    Role.DUE_DATE to RoleRequirement(Role.DUE_DATE, setOf(PropertyType.DATE)),
    Role.NOTES to RoleRequirement(Role.NOTES, setOf(PropertyType.RICH_TEXT)),
    Role.WORK_FLAG to RoleRequirement(Role.WORK_FLAG, setOf(PropertyType.CHECKBOX, PropertyType.SELECT)),
    Role.RECURRENCE_RULE to RoleRequirement(Role.RECURRENCE_RULE, setOf(PropertyType.RICH_TEXT)),
    Role.RECURRENCE_DISPLAY to RoleRequirement(Role.RECURRENCE_DISPLAY, setOf(PropertyType.RICH_TEXT)),
    Role.LABELS to RoleRequirement(Role.LABELS, setOf(PropertyType.SELECT, PropertyType.MULTI_SELECT)),
)

fun Role.allowedTypes(): Set<PropertyType> = ROLE_REQUIREMENTS.getValue(this).allowedTypes
fun Role.accepts(type: PropertyType): Boolean = type in allowedTypes()

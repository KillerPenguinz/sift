package com.ironclinicgym.sift.core.mapping

/**
 * Validates that a set of role bindings can satisfy the board before onboarding completes.
 *
 * Two checks: every required [Role] is bound, and each binding's property type is allowed
 * for its role. Failures are returned as plain-language messages (no hyphens, no em dashes)
 * suitable to show the user directly in the mapping screen.
 */
object MappingValidator {

    data class RoleIssue(val role: Role, val message: String)

    data class ValidationResult(val isSatisfied: Boolean, val issues: List<RoleIssue>) {
        val blocking: List<RoleIssue> get() = issues
    }

    /** True only when all required roles are bound to a type-compatible property. */
    fun validate(bindings: List<RolePropertyBinding>): ValidationResult {
        val issues = mutableListOf<RoleIssue>()
        val byRole = bindings.associateBy { it.role }

        for (role in Role.required) {
            val binding = byRole[role]
            if (binding == null) {
                issues += RoleIssue(role, missingMessage(role))
            } else if (!role.accepts(binding.propertyType)) {
                issues += RoleIssue(role, incompatibleMessage(role, binding))
            }
        }
        // Optional roles, if bound, must still be type-compatible.
        for (role in Role.optional) {
            val binding = byRole[role] ?: continue
            if (!role.accepts(binding.propertyType)) {
                issues += RoleIssue(role, incompatibleMessage(role, binding))
            }
        }
        return ValidationResult(isSatisfied = issues.none { it.role.required }, issues = issues)
    }

    /** A single check for live UI feedback as the user picks a property for a role. */
    fun isCompatible(role: Role, type: PropertyType): Boolean = role.accepts(type)

    private fun missingMessage(role: Role): String = when (role) {
        Role.PRIORITY -> "Choose a property for priority. It sets the urgency priorities."
        Role.BUCKET -> "Choose a property for bucket. It groups tasks by color and icon."
        Role.STATUS -> "Choose a property for status, so Sift knows what is done."
        Role.TITLE -> "Choose the title property, the name of each task."
        else -> "Choose a property for ${role.displayName()}."
    }

    private fun incompatibleMessage(role: Role, binding: RolePropertyBinding): String {
        val needed = role.allowedTypes().joinToString(" or ") { it.displayName() }
        val got = binding.propertyType.displayName()
        return "The ${role.displayName()} role needs a $needed property. " +
            "\"${binding.propertyName}\" is a $got property."
    }
}

/** Human-readable, hyphen-free role names for messages and the mapping screen. */
fun Role.displayName(): String = when (this) {
    Role.PRIORITY -> "priority"
    Role.BUCKET -> "bucket"
    Role.STATUS -> "status"
    Role.TITLE -> "title"
    Role.DUE_DATE -> "due date"
    Role.NOTES -> "notes"
    Role.WORK_FLAG -> "work flag"
    Role.RECURRENCE_RULE -> "recurrence"
    Role.RECURRENCE_DISPLAY -> "recurrence label"
    Role.LABELS -> "labels"
}

/** Human-readable, hyphen-free property type names for messages. */
fun PropertyType.displayName(): String = when (this) {
    PropertyType.TITLE -> "Title"
    PropertyType.RICH_TEXT -> "Text"
    PropertyType.NUMBER -> "Number"
    PropertyType.SELECT -> "Select"
    PropertyType.MULTI_SELECT -> "Multi select"
    PropertyType.STATUS -> "Status"
    PropertyType.DATE -> "Date"
    PropertyType.CHECKBOX -> "Checkbox"
    PropertyType.URL -> "URL"
    PropertyType.EMAIL -> "Email"
    PropertyType.PHONE -> "Phone"
    PropertyType.PEOPLE -> "People"
    PropertyType.FILES -> "Files"
    PropertyType.FORMULA -> "Formula"
    PropertyType.RELATION -> "Relation"
    PropertyType.ROLLUP -> "Rollup"
    PropertyType.CREATED_TIME -> "Created time"
    PropertyType.LAST_EDITED_TIME -> "Last edited time"
    PropertyType.UNIQUE_ID -> "Unique id"
    PropertyType.UNSUPPORTED -> "Unsupported"
}

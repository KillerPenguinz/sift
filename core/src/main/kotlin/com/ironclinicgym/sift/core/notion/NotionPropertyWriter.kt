package com.ironclinicgym.sift.core.notion

import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.Role
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the Notion `properties` object for a write — the write-path inverse of
 * [com.ironclinicgym.sift.core.domain.NotionRecordMapper].
 *
 * Every edit names a semantic [Role]; this resolves it to the user's actual property through
 * the [DatabaseMapping] and encodes the value for that property's real [PropertyType]. **This
 * is the choke point that guarantees no write ever hardcodes a Notion property name** — callers
 * express intent in roles, never in names. Selects/statuses are written by option *name* (Notion
 * matches an existing option, or creates it when it does not exist yet), which is exactly what
 * lets a brand-new Bucket or Priority sync a real option on first write.
 *
 * Pure Kotlin over kotlinx.serialization JSON; no platform types.
 */
object NotionPropertyWriter {

    /** A single field change, expressed against a role rather than a property name. */
    sealed interface PropertyEdit {
        val role: Role

        /** Title property (the page title). */
        data class TitleText(val text: String, override val role: Role = Role.TITLE) : PropertyEdit

        /** Rich-text property (notes, the recurrence fields). */
        data class RichText(override val role: Role, val text: String) : PropertyEdit

        /**
         * Select or status set by option name; Notion creates the option if it is new. A null
         * [optionName] clears the property.
         */
        data class SelectByName(override val role: Role, val optionName: String?) : PropertyEdit

        /** Date property; [iso] may be a date or date-time, or null to clear. */
        data class DateValue(override val role: Role, val iso: String?) : PropertyEdit

        /** Checkbox property (the automatic setup "Done" status). */
        data class Checkbox(override val role: Role, val checked: Boolean) : PropertyEdit

        /** Multi-value labels; written as multi_select (list) or select (first only). */
        data class Labels(val names: List<String>, override val role: Role = Role.LABELS) : PropertyEdit
    }

    /**
     * Encode [edits] into a `properties` object. Edits whose role is unbound in [mapping] are
     * skipped (an optional field the user did not map simply is not written). The concrete
     * encoding always follows the *bound property's* real type, not the edit's assumption, so a
     * status role backed by a checkbox and one backed by a status property both work.
     */
    fun buildProperties(mapping: DatabaseMapping, edits: List<PropertyEdit>): JsonObject = buildJsonObject {
        for (edit in edits) {
            val binding = mapping.propertyFor(edit.role) ?: continue
            val name = binding.propertyName
            when (binding.propertyType) {
                PropertyType.TITLE -> put(name, titleValue(textOf(edit)))
                PropertyType.RICH_TEXT -> put(name, richTextValue(textOf(edit)))
                PropertyType.SELECT -> put(name, when (edit) {
                    is PropertyEdit.Labels -> selectValue(edit.names.firstOrNull())
                    is PropertyEdit.SelectByName -> selectValue(edit.optionName)
                    else -> selectValue(null)
                })
                PropertyType.STATUS -> put(name, statusValue((edit as? PropertyEdit.SelectByName)?.optionName))
                PropertyType.CHECKBOX -> put(name, checkboxValue((edit as? PropertyEdit.Checkbox)?.checked == true))
                PropertyType.DATE -> put(name, dateValue((edit as? PropertyEdit.DateValue)?.iso))
                PropertyType.MULTI_SELECT -> put(name, when (edit) {
                    is PropertyEdit.Labels -> multiSelectValues(edit.names)
                    is PropertyEdit.SelectByName -> multiSelectValue(edit.optionName)
                    else -> multiSelectValues(emptyList())
                })
                else -> Unit // Computed/read-only types (formula, rollup, …) are never written.
            }
        }
    }

    private fun textOf(edit: PropertyEdit): String = when (edit) {
        is PropertyEdit.TitleText -> edit.text
        is PropertyEdit.RichText -> edit.text
        is PropertyEdit.SelectByName -> edit.optionName ?: ""
        is PropertyEdit.Labels -> edit.names.firstOrNull() ?: ""
        else -> ""
    }

    private fun titleValue(text: String) = buildJsonObject {
        putJsonArray("title") {
            if (text.isNotEmpty()) addJsonObject { putJsonObject("text") { put("content", text) } }
        }
    }

    private fun richTextValue(text: String) = buildJsonObject {
        putJsonArray("rich_text") {
            if (text.isNotEmpty()) addJsonObject { putJsonObject("text") { put("content", text) } }
        }
    }

    // Notion clears a value by setting the inner container to explicit JSON null.

    /** Setting select to null clears it; otherwise write by name so a new option auto-creates. */
    private fun selectValue(optionName: String?) = buildJsonObject {
        if (optionName.isNullOrBlank()) put("select", JsonNull)
        else putJsonObject("select") { put("name", optionName) }
    }

    private fun statusValue(optionName: String?) = buildJsonObject {
        if (optionName.isNullOrBlank()) put("status", JsonNull)
        else putJsonObject("status") { put("name", optionName) }
    }

    private fun multiSelectValue(optionName: String?) = buildJsonObject {
        putJsonArray("multi_select") {
            if (!optionName.isNullOrBlank()) addJsonObject { put("name", optionName) }
        }
    }

    private fun multiSelectValues(names: List<String>) = buildJsonObject {
        putJsonArray("multi_select") {
            for (n in names) { if (n.isNotBlank()) addJsonObject { put("name", n) } }
        }
    }

    private fun checkboxValue(checked: Boolean) = buildJsonObject { put("checkbox", checked) }

    private fun dateValue(iso: String?) = buildJsonObject {
        if (iso.isNullOrBlank()) put("date", JsonNull)
        else putJsonObject("date") { put("start", iso) }
    }
}

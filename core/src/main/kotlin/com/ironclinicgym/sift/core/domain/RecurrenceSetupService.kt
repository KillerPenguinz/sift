package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.ports.Consent
import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.domain.ports.RecurrenceConsentStore
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import com.ironclinicgym.sift.core.mapping.SchemaTemplate
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.NotionException
import kotlinx.serialization.json.JsonObject

/**
 * Gates the one place Sift ever adds columns to a user's database: the recurrence fields.
 *
 * Automatic setup databases ship these fields, so they are already bound and this never runs. For a
 * bring-your-own database the fields are absent; recurrence is unavailable until the user
 * consents, at which point Sift adds the two Sift-prefixed columns via `updateDataSource`, binds
 * them by id into the mapping, and remembers the consent. Declining degrades gracefully: the
 * feature stays off for that database and is not asked about again.
 */
class RecurrenceSetupService(
    private val notion: NotionClient,
    private val mappingStore: MappingStore,
    private val consentStore: RecurrenceConsentStore,
) {
    sealed interface Availability {
        /** Fields exist and are bound; recurrence is usable now. */
        data object Ready : Availability

        /** Bring-your-own database without the fields; show the consent prompt before adding them. */
        data object NeedsConsent : Availability

        /** The user declined for this database; recurrence stays off. */
        data object Declined : Availability
    }

    sealed interface AddResult {
        data class Success(val mapping: DatabaseMapping) : AddResult
        data class Failure(val message: String) : AddResult
    }

    suspend fun availability(mapping: DatabaseMapping): Availability {
        if (mapping.hasRecurrenceFields) return Availability.Ready
        return when (consentStore.get(mapping.id)) {
            Consent.DECLINED -> Availability.Declined
            else -> Availability.NeedsConsent
        }
    }

    /** The plain-language prompt shown before adding fields (no em dashes or hyphens). */
    fun consentPrompt(): String =
        "To support recurring tasks, Sift needs to add two fields called " +
            "${SchemaTemplate.PROP_RECURRENCE_RULE} and ${SchemaTemplate.PROP_RECURRENCE_DISPLAY} to your database. " +
            "Okay to add them?"

    /** User declined: remember it so recurrence stays off and we do not ask again. */
    suspend fun decline(mapping: DatabaseMapping) = consentStore.set(mapping.id, Consent.DECLINED)

    /**
     * User consented: add the two Sift columns, bind them by id, persist the updated mapping, and
     * record consent. Idempotent: if the fields already exist Notion merges without duplicating.
     */
    suspend fun addFields(mapping: DatabaseMapping): AddResult {
        val schema = try {
            notion.updateDataSource(mapping.ref.dataSourceId, buildRecurrenceSchema())
        } catch (e: NotionException) {
            return AddResult.Failure(e.message.orEmpty().ifBlank { "Could not add the recurrence fields. Please try again." })
        }

        val rule = schema.properties.firstOrNull { it.name == SchemaTemplate.PROP_RECURRENCE_RULE }
        val display = schema.properties.firstOrNull { it.name == SchemaTemplate.PROP_RECURRENCE_DISPLAY }
        if (rule == null || display == null) {
            return AddResult.Failure("Sift added the fields but could not read them back. Please try again.")
        }

        val bindings = mapping.bindings.filterNot { it.role == Role.RECURRENCE_RULE || it.role == Role.RECURRENCE_DISPLAY } +
            RolePropertyBinding(Role.RECURRENCE_RULE, rule.id, rule.name, rule.type) +
            RolePropertyBinding(Role.RECURRENCE_DISPLAY, display.id, display.name, display.type)
        val updated = mapping.copy(bindings = bindings)

        return try {
            val set = mappingStore.load()
            mappingStore.save(set.withMapping(updated, makeActive = set.activeId == updated.id))
            consentStore.set(mapping.id, Consent.GRANTED)
            AddResult.Success(updated)
        } catch (e: Exception) {
            AddResult.Failure("Sift added the fields but could not save the mapping. Please try again.")
        }
    }

    private fun buildRecurrenceSchema(): JsonObject = JsonObject(SchemaTemplate.recurrenceProperties())
}

package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.mapping.AutoMatcher
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.DataSourceRef
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.MappingDefaults
import com.ironclinicgym.sift.core.mapping.MappingValidator
import com.ironclinicgym.sift.core.mapping.OptionInput
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import com.ironclinicgym.sift.core.mapping.SchemaTemplate
import com.ironclinicgym.sift.core.mapping.BucketBinding
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.ROLE_REQUIREMENTS
import com.ironclinicgym.sift.core.mapping.accepts
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.NotionException
import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionPageSummary
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Orchestrates both onboarding doors over the [NotionClient] and the [MappingStore].
 *
 * Pure logic, no UI types: it returns sealed results that a ViewModel renders. Automatic
 * setup is modeled as discrete, resumable steps so a partial failure surfaces an actionable
 * error rather than a dead half-provisioned state. Seeding is best effort and never blocks success.
 */
class OnboardingService(
    private val notion: NotionClient,
    private val mappingStore: MappingStore,
) {
    enum class ProvisionStep { RESOLVE_PAGE, CREATE_DATABASE, RESOLVE_DATA_SOURCE, BUILD_MAPPING, SAVE_MAPPING }

    sealed interface ProvisionResult {
        data class Success(val mapping: DatabaseMapping, val seedFailures: Int) : ProvisionResult
        data class Failure(val step: ProvisionStep, val message: String) : ProvisionResult
    }

    sealed interface CompleteResult {
        data class Success(val mapping: DatabaseMapping) : CompleteResult
        data class Invalid(val issues: List<MappingValidator.RoleIssue>) : CompleteResult
        data class Failure(val message: String) : CompleteResult
    }

    data class MissingProperty(
        val role: Role,
        val propertyName: String,
        val propertyType: PropertyType,
        val description: String,
        val required: Boolean,
    )

    /** A pre-filled mapping proposal for the bring your own database screen; the user edits before completing. */
    data class MappingSuggestion(
        val bindings: List<RolePropertyBinding>,
        val priorities: List<PriorityBinding>,
        val buckets: List<BucketBinding>,
        val unmatchedPriorityOptionIds: List<String>,
        val unmatchedBucketOptionIds: List<String>,
        val missingProperties: List<MissingProperty>,
    )

    sealed interface CreateResult {
        data class Success(val schema: NotionDataSourceSchema) : CreateResult
        data class Failure(val message: String) : CreateResult
    }

    // ---- Automatic setup: recommended auto provision ----

    /**
     * Real pages the connection was granted, used to resolve where the database is created.
     * Notion search returns database *rows* as page objects too, so we keep only true pages
     * (parent workspace or page_id) and drop rows, which otherwise flood the picker.
     */
    suspend fun listGrantedPages(): List<NotionPageSummary> =
        notion.searchPages().filter { it.parentType == "workspace" || it.parentType == "page_id" }

    /**
     * Provision into the first granted page when the caller does not specify one. The grant
     * step (Notion's page picker) is where the user already chose the location, so there is no
     * separate post-auth picker.
     * TODO(BJ): when more than one page is granted, confirm the rule (currently first granted).
     */
    suspend fun provisionAutoSetup(
        workspaceId: String,
        bucketNames: List<String> = SchemaTemplate.defaultBucketNames(),
    ): ProvisionResult {
        val pageId = try {
            listGrantedPages().firstOrNull()?.pageId
                ?: return ProvisionResult.Failure(
                    ProvisionStep.RESOLVE_PAGE,
                    "Sift could not find a page you granted access to. Please grant a page and try again.",
                )
        } catch (e: NotionException) {
            return ProvisionResult.Failure(ProvisionStep.RESOLVE_PAGE, e.message.orEmpty())
        }
        return provisionAutoSetup(workspaceId, pageId, bucketNames)
    }

    suspend fun provisionAutoSetup(
        workspaceId: String,
        parentPageId: String,
        bucketNames: List<String> = SchemaTemplate.defaultBucketNames(),
    ): ProvisionResult {
        val summary = try {
            notion.createDatabase(
                parentPageId = parentPageId,
                title = SchemaTemplate.DATABASE_TITLE,
                properties = SchemaTemplate.buildCreateProperties(bucketNames),
            )
        } catch (e: NotionException) {
            return ProvisionResult.Failure(ProvisionStep.CREATE_DATABASE, e.message.orEmpty())
        }

        val schema: NotionDataSourceSchema = try {
            val dataSourceId = summary.dataSources.firstOrNull()?.id
                ?: notion.retrieveDatabase(summary.databaseId).dataSources.firstOrNull()?.id
                ?: return ProvisionResult.Failure(
                    ProvisionStep.RESOLVE_DATA_SOURCE,
                    "Sift created the database but could not find its data source.",
                )
            notion.retrieveDataSource(dataSourceId)
        } catch (e: NotionException) {
            return ProvisionResult.Failure(ProvisionStep.RESOLVE_DATA_SOURCE, e.message.orEmpty())
        }

        val ref = DataSourceRef(workspaceId, schema.databaseId.ifBlank { summary.databaseId }, schema.dataSourceId)
        val mapping = try {
            SchemaTemplate.buildMapping(ref, schema)
        } catch (e: Exception) {
            return ProvisionResult.Failure(ProvisionStep.BUILD_MAPPING, "Could not build the mapping from the new database.")
        }

        // Seed rows are best effort: a failed example must not leave a dead state.
        var seedFailures = 0
        SchemaTemplate.buildSeedRows().forEach { row ->
            runCatching { notion.createPage(schema.dataSourceId, row) }.onFailure { seedFailures++ }
        }

        return try {
            persist(mapping)
            ProvisionResult.Success(mapping, seedFailures)
        } catch (e: Exception) {
            ProvisionResult.Failure(ProvisionStep.SAVE_MAPPING, "Setup finished but the mapping could not be saved.")
        }
    }

    // ---- Bring your own database ----

    suspend fun listDatabases(query: String? = null): List<NotionDataSourceSchema> =
        notion.searchDataSources(query)

    suspend fun loadSchema(dataSourceId: String): NotionDataSourceSchema =
        notion.retrieveDataSource(dataSourceId)

    sealed interface SchemaResult {
        data class Success(val schema: NotionDataSourceSchema) : SchemaResult
        data class NoAccess(val message: String) : SchemaResult
    }

    // Temporary diagnostic logger — set from the app layer.
    var debugLogger: ((String) -> Unit)? = null
    private fun debugLog(msg: String) { debugLogger?.invoke(msg) }

    /**
     * Resolve an ID from a pasted Notion URL to a full data source schema.
     *
     * The URL contains a database ID. We call GET /databases/{id} (which in 2025-09-03
     * returns the data_sources array, not properties), take the first data source ID,
     * then GET /data_sources/{dsId} for the full schema. If the ID turns out to be a
     * page ID instead, we look inside the page for a child_database block and retry.
     */
    suspend fun loadSchemaFromPastedId(id: String): SchemaResult {
        debugLog("PASTE: loadSchemaFromPastedId called with id=$id")

        // Try as a database ID: GET /databases/{id} → discover data source → full schema
        val schema = resolveViaDatabase(id)
        if (schema != null) {
            debugLog("PASTE: SUCCESS via direct database resolve, title=${schema.title}")
            return SchemaResult.Success(schema)
        }

        // Maybe it's a page that contains a database; find child_database blocks
        debugLog("PASTE: direct resolve failed, trying as page with child databases")
        val childDbIds = try {
            notion.findChildDatabaseIds(id)
        } catch (e: Exception) {
            debugLog("PASTE: findChildDatabaseIds threw: ${e::class.simpleName} ${e.message}")
            emptyList()
        }
        debugLog("PASTE: found ${childDbIds.size} child database IDs: $childDbIds")
        for (childId in childDbIds) {
            debugLog("PASTE: trying child database $childId")
            val childSchema = resolveViaDatabase(childId)
            if (childSchema != null) {
                debugLog("PASTE: SUCCESS via child database, title=${childSchema.title}")
                return SchemaResult.Success(childSchema)
            }
        }

        debugLog("PASTE: ALL resolution paths failed for id=$id")
        return SchemaResult.NoAccess(
            "Could not find a database at this URL. " +
                "Try using the search picker, or make sure the database is shared with Sift."
        )
    }

    private suspend fun resolveViaDatabase(databaseId: String): NotionDataSourceSchema? {
        debugLog("RESOLVE: step 1 — retrieveDatabase($databaseId)")
        val summary = try {
            notion.retrieveDatabase(databaseId)
        } catch (e: Exception) {
            debugLog("RESOLVE: retrieveDatabase threw: ${e::class.simpleName} ${e.message}")
            return null
        }
        debugLog("RESOLVE: got summary title=${summary.title}, dataSources=${summary.dataSources.map { it.id }}")
        val dsId = summary.dataSources.firstOrNull()?.id
        if (dsId == null) {
            debugLog("RESOLVE: NO data sources in summary, returning null")
            return null
        }
        debugLog("RESOLVE: step 2 — retrieveDataSource($dsId)")
        return try {
            val schema = notion.retrieveDataSource(dsId)
            debugLog("RESOLVE: got schema title=${schema.title}, props=${schema.properties.size}")
            schema
        } catch (e: Exception) {
            debugLog("RESOLVE: retrieveDataSource threw: ${e::class.simpleName} ${e.message}")
            null
        }
    }

    /** Heuristic auto-fill for the mapping screen: bind roles by type + name synonym, auto-match options. */
    fun suggestMapping(schema: NotionDataSourceSchema, language: String = "en"): MappingSuggestion {
        val bindings = AutoMatcher.matchRoles(schema, language)

        val priorityProp = bindings.firstOrNull { it.role == Role.PRIORITY }
        val bucketProp = bindings.firstOrNull { it.role == Role.BUCKET }
        val priorityMatch = AutoMatcher.matchPriorities(optionsFor(schema, priorityProp?.propertyId), language)
        val bucketMatch = AutoMatcher.matchBuckets(optionsFor(schema, bucketProp?.propertyId), language)

        return MappingSuggestion(
            bindings = bindings,
            priorities = priorityMatch.bindings,
            buckets = bucketMatch.bindings,
            unmatchedPriorityOptionIds = priorityMatch.unmatchedOptionIds,
            unmatchedBucketOptionIds = bucketMatch.unmatchedOptionIds,
            missingProperties = findMissingProperties(schema, bindings),
        )
    }

    suspend fun completeByo(
        workspaceId: String,
        schema: NotionDataSourceSchema,
        bindings: List<RolePropertyBinding>,
        priorities: List<PriorityBinding>,
        buckets: List<BucketBinding>,
        label: String,
    ): CompleteResult {
        val validation = MappingValidator.validate(bindings)
        if (!validation.isSatisfied) return CompleteResult.Invalid(validation.issues)

        val ref = DataSourceRef(workspaceId, schema.databaseId, schema.dataSourceId)
        // Resolve which status option means "done" now, from the schema, so the write layer never
        // has to guess or re-fetch. Null for a checkbox status (complete is simply `true`).
        val doneStatus = MappingDefaults.resolveDoneStatus(schema, bindings.firstOrNull { it.role == Role.STATUS })
        val mapping = DatabaseMapping(
            id = "mapping-${schema.dataSourceId}",
            ref = ref,
            label = label.ifBlank { schema.title },
            bindings = bindings,
            priorities = priorities,
            buckets = buckets,
            doneStatus = doneStatus,
        )
        return try {
            persist(mapping)
            CompleteResult.Success(mapping)
        } catch (e: Exception) {
            CompleteResult.Failure("Could not save the mapping. Please try again.")
        }
    }

    // ---- Missing property creation for minimal databases ----

    fun findMissingProperties(
        schema: NotionDataSourceSchema,
        bindings: List<RolePropertyBinding>,
    ): List<MissingProperty> {
        val bound = bindings.map { it.role }.toSet()
        val usedPropertyIds = bindings.map { it.propertyId }.toSet()
        return CREATABLE_PROPERTIES.filter { mp ->
            mp.role !in bound && schema.properties.none { mp.role.accepts(it.type) && it.id !in usedPropertyIds }
        }
    }

    suspend fun createMissingProperties(
        dataSourceId: String,
        properties: List<MissingProperty>,
    ): CreateResult {
        if (properties.isEmpty()) return CreateResult.Failure("Nothing to create.")
        val fragment = buildJsonObject {
            properties.forEach { mp ->
                putJsonObject(mp.propertyName) {
                    val notionType = mp.propertyType.toNotionType()
                    if (mp.role == Role.BUCKET) {
                        putJsonObject(notionType) {
                            putJsonArray("options") {
                                SchemaTemplate.defaultBucketNames().forEach { name ->
                                    addJsonObject { put("name", name) }
                                }
                            }
                        }
                    } else {
                        putJsonObject(notionType) {}
                    }
                }
            }
        }
        return try {
            CreateResult.Success(notion.updateDataSource(dataSourceId, fragment))
        } catch (e: NotionException) {
            CreateResult.Failure(
                e.message?.ifBlank { null }
                    ?: "Could not add properties to your database. Please try again.",
            )
        }
    }

    private suspend fun persist(mapping: DatabaseMapping) {
        val current = mappingStore.load()
        mappingStore.save(current.withMapping(mapping, makeActive = true))
    }

    private fun optionsFor(schema: NotionDataSourceSchema, propertyId: String?): List<OptionInput> {
        if (propertyId == null) return emptyList()
        val prop = schema.properties.firstOrNull { it.id == propertyId } ?: return emptyList()
        return prop.options.map { OptionInput(it.id ?: it.name, it.name, it.color) }
    }

    companion object {
        val CREATABLE_PROPERTIES = listOf(
            MissingProperty(Role.BUCKET, "Bucket", PropertyType.SELECT, "groups tasks by color and icon", required = false),
            MissingProperty(Role.STATUS, "Done", PropertyType.CHECKBOX, "lets Sift know when a task is finished", required = true),
            MissingProperty(Role.DUE_DATE, "Due date", PropertyType.DATE, "lets you set deadlines", required = false),
            MissingProperty(Role.NOTES, "Notes", PropertyType.RICH_TEXT, "add details to tasks", required = false),
            MissingProperty(Role.LABELS, "Labels", PropertyType.MULTI_SELECT, "tag and filter tasks", required = false),
        )
    }
}

private fun PropertyType.toNotionType(): String = when (this) {
    PropertyType.SELECT -> "select"
    PropertyType.MULTI_SELECT -> "multi_select"
    PropertyType.CHECKBOX -> "checkbox"
    PropertyType.DATE -> "date"
    PropertyType.RICH_TEXT -> "rich_text"
    else -> name.lowercase()
}

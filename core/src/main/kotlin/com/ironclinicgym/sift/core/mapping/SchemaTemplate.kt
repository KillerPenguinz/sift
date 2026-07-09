package com.ironclinicgym.sift.core.mapping

import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.theme.PRIORITY_META
import com.ironclinicgym.sift.core.theme.BUCKETS
import com.ironclinicgym.sift.core.theme.BucketKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Automatic setup's opinionated database template.
 *
 * Produces the Notion create-database schema, the seed rows, and the resulting
 * [DatabaseMapping] derived from the *created* schema. The property names below are the only
 * place in the app where literal names appear, and they exist solely to author the database
 * and immediately build the mapping. Every later read/write resolves through the mapping by
 * property id, never by these names.
 *
 * Priority options and the default buckets come from the theme tokens ([PRIORITY_META],
 * [BUCKETS]) so there is one source of truth for the urgency scale and seeded buckets.
 */
object SchemaTemplate {

    // Authored property names (creation-time only).
    const val PROP_TITLE = "Title"
    const val PROP_PRIORITY = "Priority"
    const val PROP_BUCKET = "Bucket"
    const val PROP_DONE = "Done"

    // Sift-managed recurrence fields. The Sift prefix makes provenance obvious in Notion. These
    // are the ONLY columns Sift ever adds to a user's database, and only with consent (byo path)
    // or as part of Sift's own schema (automatic setup). Bound by id after creation; never read by name.
    const val PROP_RECURRENCE_RULE = "Sift Recurrence"
    const val PROP_RECURRENCE_DISPLAY = "Sift Repeats"

    const val DATABASE_TITLE = "Sift Tasks"

    // Priority -> Notion option color, warm to cool, echoing the urgency temperature.
    private val priorityColors = mapOf(
        "asap" to "red", "today" to "orange", "tomorrow" to "yellow",
        "soon" to "green", "later" to "blue", "one day" to "purple",
    )
    private val bucketColors = mapOf(
        BucketKey.WORK to "blue", BucketKey.PERSONAL to "red",
    )

    /** A seed example row. Bucket values must match seeded bucket option names. */
    private data class SeedRow(val title: String, val priority: String, val bucket: String)

    // No hyphens or em dashes in any user-facing text.
    private val seedRows = listOf(
        SeedRow("Reply to the lease renewal email", "asap", "Personal"),
        SeedRow("Plan the weekend trip", "soon", "Personal"),
    )

    /** The `properties` object for create-database. [bucketNames] lets the user edit seeded buckets. */
    fun buildCreateProperties(bucketNames: List<String> = defaultBucketNames()): JsonObject =
        buildJsonObject {
            putJsonObject(PROP_TITLE) { putJsonObject("title") {} }
            putJsonObject(PROP_PRIORITY) {
                putJsonObject("select") {
                    putJsonArray("options") {
                        PRIORITY_META.forEach { priority ->
                            addJsonObject {
                                put("name", priority.name)
                                priorityColors[priority.name]?.let { put("color", it) }
                            }
                        }
                    }
                }
            }
            putJsonObject(PROP_BUCKET) {
                putJsonObject("select") {
                    putJsonArray("options") {
                        bucketNames.forEach { name -> addJsonObject { put("name", name) } }
                    }
                }
            }
            // Automatic setup uses a checkbox "Done" for the status role (the spec allows
            // "a status or done property"). A Notion "status" property's options/groups are
            // awkward to author via the API, so a checkbox keeps it to one clean call. Byo
            // users may still map the status role to a real status property.
            putJsonObject(PROP_DONE) { putJsonObject("checkbox") {} }
            // Automatic setup ships the recurrence fields up front (no prompt).
            recurrenceProperties().forEach { (name, schema) -> put(name, schema) }
        }

    /**
     * The two Sift recurrence columns as a schema fragment. Reused by automatic setup creation
     * and by the byo consented `updateDataSource`, so there is one definition of these fields.
     */
    fun recurrenceProperties(): Map<String, JsonObject> = linkedMapOf(
        PROP_RECURRENCE_RULE to buildJsonObject { putJsonObject("rich_text") {} },
        PROP_RECURRENCE_DISPLAY to buildJsonObject { putJsonObject("rich_text") {} },
    )

    /** Seed row property bodies for createPage. */
    fun buildSeedRows(): List<JsonObject> = seedRows.map { row ->
        buildJsonObject {
            putJsonObject(PROP_TITLE) {
                putJsonArray("title") {
                    addJsonObject { putJsonObject("text") { put("content", row.title) } }
                }
            }
            putJsonObject(PROP_PRIORITY) { putJsonObject("select") { put("name", row.priority) } }
            putJsonObject(PROP_BUCKET) { putJsonObject("select") { put("name", row.bucket) } }
            putJsonObject(PROP_DONE) { put("checkbox", false) }
        }
    }

    fun defaultBucketNames(): List<String> = BUCKETS.values.map { it.label }

    /**
     * Build the mapping from the freshly created schema. Resolves roles by the authored names
     * (creation-time) but stores property *ids*, so all later access is id-based via the
     * mapping. Priorities/buckets inherit default keys for color and order reuse.
     */
    fun buildMapping(ref: DataSourceRef, schema: NotionDataSourceSchema): DatabaseMapping {
        fun prop(name: String) = schema.properties.firstOrNull { it.name == name }

        val title = schema.properties.firstOrNull { it.type == PropertyType.TITLE } ?: prop(PROP_TITLE)
        val priority = prop(PROP_PRIORITY)
        val bucket = prop(PROP_BUCKET)
        val done = prop(PROP_DONE)
        val recurrenceRule = prop(PROP_RECURRENCE_RULE)
        val recurrenceDisplay = prop(PROP_RECURRENCE_DISPLAY)

        val bindings = buildList {
            title?.let { add(RolePropertyBinding(Role.TITLE, it.id, it.name, it.type)) }
            priority?.let { add(RolePropertyBinding(Role.PRIORITY, it.id, it.name, it.type)) }
            bucket?.let { add(RolePropertyBinding(Role.BUCKET, it.id, it.name, it.type)) }
            done?.let { add(RolePropertyBinding(Role.STATUS, it.id, it.name, it.type)) }
            recurrenceRule?.let { add(RolePropertyBinding(Role.RECURRENCE_RULE, it.id, it.name, it.type)) }
            recurrenceDisplay?.let { add(RolePropertyBinding(Role.RECURRENCE_DISPLAY, it.id, it.name, it.type)) }
        }

        // Priorities come from the created priority options, in the authored urgency order.
        val orderByName = PRIORITY_META.withIndex().associate { (i, m) -> m.name to (m.key.name to i) }
        val priorities = (priority?.options ?: emptyList()).map { opt ->
            val match = orderByName[opt.name]
            PriorityBinding(
                optionId = opt.id ?: opt.name,
                optionName = opt.name,
                order = match?.second ?: Int.MAX_VALUE,
                priorityKey = match?.first,
                colorHint = opt.color,
            )
        }.sortedBy { it.order }

        val bucketKeyByLabel = BUCKETS.entries.associate { (k, v) -> v.label to k.name }
        val buckets = (bucket?.options ?: emptyList()).map { opt ->
            BucketBinding(
                optionId = opt.id ?: opt.name,
                optionName = opt.name,
                bucketKey = bucketKeyByLabel[opt.name],
                colorHint = opt.color,
            )
        }

        return DatabaseMapping(
            id = "mapping-${ref.dataSourceId}",
            ref = ref,
            label = schema.title.ifBlank { DATABASE_TITLE },
            bindings = bindings,
            priorities = priorities,
            buckets = buckets,
        )
    }
}

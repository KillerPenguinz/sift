package com.ironclinicgym.sift.core.notion

import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionDatabaseSummary
import com.ironclinicgym.sift.core.notion.model.NotionListEnvelope
import com.ironclinicgym.sift.core.notion.model.NotionPageResult
import com.ironclinicgym.sift.core.notion.model.NotionPageSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The high-level Notion API surface for Sift, built on a [HttpTransport].
 *
 * Every call is rate limited (shared [RateLimiter]) and retried with [Backoff]; every list
 * call paginates with an explicit `page_size` of [NOTION_PAGE_SIZE]. The client is
 * data-source aware: it queries `/data_sources/{id}/query`, not the legacy database query.
 *
 * Capabilities used are exactly Read, Insert, Update content. No user-information calls.
 * Pure Kotlin; the bearer token is supplied lazily via [authTokenProvider] so the secure
 * store stays in the platform layer.
 */
class NotionClient(
    private val transport: HttpTransport,
    private val authTokenProvider: suspend () -> String?,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val backoff: Backoff = Backoff(),
    // Page creation is not idempotent (Notion has no idempotency key), so ambiguous failures
    // must NOT auto-retry or a lost acknowledgement could duplicate a task. The write layer
    // reconciles instead. 429 is still retried (that request was rejected, never processed).
    private val createBackoff: Backoff = Backoff(retryAmbiguous = false),
    private val baseUrl: String = "https://api.notion.com/v1",
    private val notionVersion: String = NOTION_VERSION,
) {
    private val json = NotionParser.json

    // Temporary diagnostic logger — set from the app layer to pipe into Android Log.d.
    var debugLogger: ((String) -> Unit)? = null
    private fun debugLog(msg: String) { debugLogger?.invoke(msg) }

    /** List the data sources the connection can see (bring your own database picker). */
    suspend fun searchDataSources(query: String? = null): List<NotionDataSourceSchema> {
        debugLog("SEARCH: starting searchDataSources, query=${query ?: "(none)"}")
        val raw = paginateAll { cursor ->
            val body = buildJsonObject {
                query?.let { put("query", it) }
                putJsonObject("filter") {
                    put("property", "object")
                    put("value", "data_source")
                }
                put("page_size", NOTION_PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            }
            debugLog("SEARCH: request body=${json.encodeToString(JsonObject.serializer(), body)}")
            send(HttpMethod.POST, "/search", body).toPage()
        }
        debugLog("SEARCH: got ${raw.size} raw results")
        raw.forEachIndexed { i, obj ->
            val objType = obj["object"]?.jsonPrimitive?.content ?: "?"
            val id = obj["id"]?.jsonPrimitive?.content ?: "?"
            val name = obj["name"]?.toString()?.take(80) ?: obj["title"]?.toString()?.take(80) ?: "?"
            debugLog("SEARCH result[$i]: object=$objType, id=$id, name=$name")
        }
        return raw.map { NotionParser.parseDataSourceSchema(it) }
    }

    /** List the pages the connection can see, to resolve where automatic setup creates its database. */
    suspend fun searchPages(query: String? = null): List<NotionPageSummary> =
        paginateAll { cursor ->
            val body = buildJsonObject {
                query?.let { put("query", it) }
                putJsonObject("filter") {
                    put("property", "object")
                    put("value", "page")
                }
                put("page_size", NOTION_PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            }
            send(HttpMethod.POST, "/search", body).toPage()
        }.map { NotionParser.parsePageSummary(it) }

    /** Retrieve a database to resolve its data source id(s) (used after automatic setup creation). */
    suspend fun retrieveDatabase(databaseId: String): NotionDatabaseSummary {
        debugLog("RETRIEVE_DB: GET /databases/$databaseId")
        val raw = send(HttpMethod.GET, "/databases/$databaseId", null)
        debugLog("RETRIEVE_DB: response keys=${raw.keys.joinToString()}")
        val summary = NotionParser.parseDatabaseSummary(raw)
        debugLog("RETRIEVE_DB: parsed title=${summary.title}, dataSources=${summary.dataSources.map { "${it.id}(${it.name})" }}")
        return summary
    }

    /** Retrieve a data source's full property schema (bring your own database mapping screen). */
    suspend fun retrieveDataSource(dataSourceId: String): NotionDataSourceSchema {
        debugLog("RETRIEVE_DS: GET /data_sources/$dataSourceId")
        val raw = send(HttpMethod.GET, "/data_sources/$dataSourceId", null)
        debugLog("RETRIEVE_DS: response keys=${raw.keys.joinToString()}")
        val schema = NotionParser.parseDataSourceSchema(raw)
        debugLog("RETRIEVE_DS: parsed title=${schema.title}, databaseId=${schema.databaseId}, dsId=${schema.dataSourceId}, props=${schema.properties.size}")
        return schema
    }

    /** Quick probe: can this id be queried as a data source? Returns true if the endpoint accepts it. */
    suspend fun probeDataSource(dataSourceId: String): Boolean = try {
        val body = buildJsonObject { put("page_size", 1) }
        send(HttpMethod.POST, "/data_sources/$dataSourceId/query", body)
        true
    } catch (_: NotionException) {
        false
    }

    /** Query all records of a data source. Returns raw page objects (Phase 2 maps them). */
    suspend fun queryDataSource(dataSourceId: String): List<JsonObject> =
        paginateAll { cursor ->
            val body = buildJsonObject {
                put("page_size", NOTION_PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            }
            send(HttpMethod.POST, "/data_sources/$dataSourceId/query", body).toPage()
        }

    /** Read a page's body as plain-text lines (Read content capability; no comments). */
    suspend fun retrievePageBlocks(pageId: String): List<String> =
        paginateAll { cursor ->
            val query = buildString {
                append("?page_size=").append(NOTION_PAGE_SIZE)
                if (cursor != null) append("&start_cursor=").append(cursor)
            }
            send(HttpMethod.GET, "/blocks/$pageId/children$query", null).toPage()
        }.mapNotNull { NotionParser.parseBlockText(it) }

    /** Find child database IDs inside a page (for resolving pasted page URLs). */
    suspend fun findChildDatabaseIds(pageId: String): List<String> {
        debugLog("FIND_CHILDREN: GET /blocks/$pageId/children")
        val blocks = paginateAll { cursor ->
            val query = buildString {
                append("?page_size=").append(NOTION_PAGE_SIZE)
                if (cursor != null) append("&start_cursor=").append(cursor)
            }
            send(HttpMethod.GET, "/blocks/$pageId/children$query", null).toPage()
        }
        debugLog("FIND_CHILDREN: got ${blocks.size} blocks")
        blocks.forEachIndexed { i, b ->
            val t = b["type"]?.jsonPrimitive?.content ?: "?"
            val id = b["id"]?.jsonPrimitive?.content ?: "?"
            debugLog("FIND_CHILDREN block[$i]: type=$t, id=$id")
        }
        val childDbIds = blocks.filter { it["type"]?.jsonPrimitive?.content == "child_database" }
            .mapNotNull { it["id"]?.jsonPrimitive?.content }
        debugLog("FIND_CHILDREN: found ${childDbIds.size} child_database ids: $childDbIds")
        return childDbIds
    }

    /**
     * Create a database inside a granted page (automatic setup). [properties] is the schema object,
     * [title] the plain database name. Returns the created database with its data source id.
     */
    suspend fun createDatabase(
        parentPageId: String,
        title: String,
        properties: JsonObject,
    ): NotionDatabaseSummary {
        // 2025-09-03: the schema goes under initial_data_source.properties, not top-level
        // properties, to separate data-source schema from database-wide settings.
        val body = buildJsonObject {
            putJsonObject("parent") {
                put("type", "page_id")
                put("page_id", parentPageId)
            }
            put("title", richText(title))
            putJsonObject("initial_data_source") {
                put("properties", properties)
            }
        }
        return NotionParser.parseDatabaseSummary(send(HttpMethod.POST, "/databases", body))
    }

    /**
     * Insert a record into a data source (automatic setup seed rows, add-task). Uses [createBackoff] so
     * an ambiguous failure does not silently duplicate the task; the caller reconciles.
     */
    suspend fun createPage(dataSourceId: String, properties: JsonObject): NotionPageResult {
        // Confirmed for 2025-09-03: pages under a data source use a data_source_id parent.
        val body = buildJsonObject {
            putJsonObject("parent") {
                put("type", "data_source_id")
                put("data_source_id", dataSourceId)
            }
            put("properties", properties)
        }
        return NotionPageResult(NotionParser.parsePageId(send(HttpMethod.POST, "/pages", body, backoff = createBackoff)))
    }

    /** Update a record's properties. Idempotent (same PATCH twice yields the same state). */
    suspend fun updatePage(pageId: String, properties: JsonObject): NotionPageResult {
        val body = buildJsonObject { put("properties", properties) }
        return NotionPageResult(NotionParser.parsePageId(send(HttpMethod.PATCH, "/pages/$pageId", body)))
    }

    /**
     * Archive a page to Notion trash (Remove) or restore it (undo). Recoverable, never a hard
     * delete. Idempotent: setting the same archived flag twice is a no-op server-side.
     */
    suspend fun archivePage(pageId: String, archived: Boolean = true): NotionPageResult {
        val body = buildJsonObject { put("in_trash", archived) }
        return NotionPageResult(NotionParser.parsePageId(send(HttpMethod.PATCH, "/pages/$pageId", body)))
    }

    /**
     * Add or update properties on a data source's schema (add a select option, or the Sift
     * recurrence columns after consent). [properties] is a partial schema object; Notion merges
     * it. Returns the refreshed schema so the caller can bind the new property ids.
     */
    suspend fun updateDataSource(dataSourceId: String, properties: JsonObject): NotionDataSourceSchema {
        val body = buildJsonObject { put("properties", properties) }
        return NotionParser.parseDataSourceSchema(send(HttpMethod.PATCH, "/data_sources/$dataSourceId", body))
    }

    /**
     * Find recent pages whose title equals [title], newest first. Used to reconcile an ambiguous
     * create: if the page already exists we adopt it instead of creating a duplicate. Filters and
     * sorts by the mapped title property id, so no property name is hardcoded.
     */
    suspend fun findPagesByTitle(dataSourceId: String, titlePropertyId: String, title: String, limit: Int = 5): List<JsonObject> {
        val body = buildJsonObject {
            putJsonObject("filter") {
                put("property", titlePropertyId)
                putJsonObject("title") { put("equals", title) }
            }
            putJsonArray("sorts") {
                addJsonObject {
                    put("timestamp", "created_time")
                    put("direction", "descending")
                }
            }
            put("page_size", limit)
        }
        val page = send(HttpMethod.POST, "/data_sources/$dataSourceId/query", body).toPage()
        return page.items
    }

    // ---- internals ----

    private fun JsonObject.toPage(): NotionPage<JsonObject> {
        val env = json.decodeFromJsonElement(NotionListEnvelope.serializer(), this)
        return NotionPage(env.results, env.nextCursor, env.hasMore)
    }

    private suspend fun send(method: HttpMethod, path: String, body: JsonObject?, backoff: Backoff = this.backoff): JsonObject {
        val token = authTokenProvider() ?: throw NotionException.Unauthorized().also {
            debugLog("SEND: NO TOKEN available, throwing Unauthorized")
        }
        debugLog("SEND: $method $baseUrl$path")
        if (body != null) debugLog("SEND: body=${json.encodeToString(JsonObject.serializer(), body).take(500)}")
        val request = HttpRequest(
            method = method,
            url = baseUrl + path,
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Notion-Version" to notionVersion,
                "Content-Type" to "application/json",
            ),
            body = body?.let { json.encodeToString(JsonObject.serializer(), it) },
        )
        val response = try {
            backoff.retry {
                val r = try {
                    rateLimiter.withPermit { transport.execute(request) }
                } catch (e: NotionException) {
                    debugLog("SEND: NotionException during transport: ${e::class.simpleName} ${e.message}")
                    throw e
                } catch (e: Throwable) {
                    debugLog("SEND: Network error: ${e::class.simpleName} ${e.message}")
                    throw NotionException.Network(e)
                }
                debugLog("SEND: HTTP ${r.code}, body(${r.body.length} chars)=${r.body.take(500)}")
                if (!r.isSuccess) {
                    val ex = r.toException()
                    debugLog("SEND: API error: ${ex::class.simpleName} ${ex.message}")
                    throw ex
                }
                r
            }
        } catch (e: NotionException) {
            debugLog("SEND: FINAL exception after retries: ${e::class.simpleName} ${e.message}")
            throw e
        }
        return json.parseToJsonElement(response.body).jsonObject
    }

    private fun HttpResponse.toException(): NotionException {
        val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val notionCode = parsed?.get("code")?.jsonPrimitive?.content
        val message = parsed?.get("message")?.jsonPrimitive?.content ?: "Notion request failed ($code)."
        return when (code) {
            401 -> NotionException.Unauthorized()
            429 -> NotionException.RateLimited(header("Retry-After")?.toDoubleOrNull())
            400, 409 -> NotionException.Validation(notionCode, message)
            else -> NotionException.Api(code, notionCode, message)
        }
    }

    private fun richText(content: String) = kotlinx.serialization.json.buildJsonArray {
        add(buildJsonObject {
            put("type", "text")
            putJsonObject("text") { put("content", content) }
        })
    }

    companion object {
        /** Notion API version that introduces data sources as the primary abstraction. */
        const val NOTION_VERSION: String = "2025-09-03"
    }
}

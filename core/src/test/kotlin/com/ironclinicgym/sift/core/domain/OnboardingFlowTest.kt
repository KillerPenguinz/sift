package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.notion.FakeTransport
import com.ironclinicgym.sift.core.notion.HttpMethod
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.RateLimiter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    private val createDbResponse =
        """{"id":"db1","title":[{"plain_text":"Sift Tasks"}],"data_sources":[{"id":"ds1","name":"Sift Tasks"}]}"""

    private val dataSourceSchema = """
        {"id":"ds1","parent":{"database_id":"db1"},"name":[{"plain_text":"Sift Tasks"}],"properties":{
          "Title":{"id":"title","type":"title","title":{}},
          "Priority":{"id":"pr","type":"select","select":{"options":[
            {"id":"b0","name":"asap","color":"red"},{"id":"b1","name":"today","color":"orange"},
            {"id":"b2","name":"tomorrow","color":"yellow"},{"id":"b3","name":"soon","color":"green"},
            {"id":"b4","name":"later","color":"blue"},{"id":"b5","name":"one day","color":"purple"}]}},
          "Bucket":{"id":"sr","type":"select","select":{"options":[
            {"id":"s0","name":"Personal"},{"id":"s1","name":"Work"},
            {"id":"s2","name":"Health"},{"id":"s3","name":"Side project"}]}},
          "Done":{"id":"dn","type":"checkbox","checkbox":{}}
        }}
    """.trimIndent()

    private fun client(transport: FakeTransport) =
        NotionClient(transport, authTokenProvider = { "tok" }, rateLimiter = RateLimiter(sleep = {}))

    @Test fun `automatic setup provisions a database, builds the mapping, and persists it active`() = runTest {
        val transport = FakeTransport()
            .on(HttpMethod.POST, "/databases", body = createDbResponse)
            .on(HttpMethod.GET, "/data_sources/ds1", body = dataSourceSchema)
            .on(HttpMethod.POST, "/pages", body = """{"id":"seed"}""")
        val store = FakeMappingStore()
        val service = OnboardingService(client(transport), store)

        val result = service.provisionAutoSetup(workspaceId = "ws1", parentPageId = "parent1")

        assertTrue(result is OnboardingService.ProvisionResult.Success)
        result as OnboardingService.ProvisionResult.Success
        assertEquals(0, result.seedFailures)
        assertEquals(4, result.mapping.bindings.size)
        assertEquals(6, result.mapping.priorities.size)
        assertEquals("asap", result.mapping.priorities.first().optionName)
        assertEquals(4, result.mapping.buckets.size)
        assertEquals("ds1", result.mapping.ref.dataSourceId)
        assertEquals("db1", result.mapping.ref.databaseId)
        // Persisted and active.
        assertEquals(result.mapping.id, store.load().active?.id)
        assertEquals("pr", result.mapping.requireProperty(Role.PRIORITY).propertyId)
    }

    @Test fun `automatic setup reports an actionable failure when database creation fails`() = runTest {
        val transport = FakeTransport()
            .on(HttpMethod.POST, "/databases", code = 400,
                body = """{"code":"validation_error","message":"bad parent"}""")
        val service = OnboardingService(client(transport), FakeMappingStore())

        val result = service.provisionAutoSetup("ws1", "parent1")
        assertTrue(result is OnboardingService.ProvisionResult.Failure)
        assertEquals(
            OnboardingService.ProvisionStep.CREATE_DATABASE,
            (result as OnboardingService.ProvisionResult.Failure).step,
        )
    }

    @Test fun `byo suggestion auto-binds roles by type and name hint`() = runTest {
        val transport = FakeTransport().on(HttpMethod.GET, "/data_sources/ds1", body = dataSourceSchema)
        val service = OnboardingService(client(transport), FakeMappingStore())

        val schema = service.loadSchema("ds1")
        val suggestion = service.suggestMapping(schema)

        assertEquals("pr", suggestion.bindings.first { it.role == Role.PRIORITY }.propertyId)
        assertEquals("sr", suggestion.bindings.first { it.role == Role.BUCKET }.propertyId)
        assertEquals("title", suggestion.bindings.first { it.role == Role.TITLE }.propertyId)
        assertEquals(6, suggestion.priorities.size)
    }

    @Test fun `listGrantedPages keeps only real pages and drops database rows`() = runTest {
        // Notion search returns database rows as page objects too; only workspace/page_id parents
        // are real pages. The rows (parent database_id / data_source_id) must be filtered out.
        val searchResult = """
            {"results":[
              {"object":"page","id":"pworkspace","parent":{"type":"workspace"},
               "properties":{"t":{"type":"title","title":[{"plain_text":"My Notes"}]}}},
              {"object":"page","id":"psub","parent":{"type":"page_id","page_id":"pworkspace"},
               "properties":{"t":{"type":"title","title":[{"plain_text":"Projects"}]}}},
              {"object":"page","id":"prow1","parent":{"type":"database_id","database_id":"db1"},
               "properties":{"t":{"type":"title","title":[{"plain_text":"Reply to the lease renewal email"}]}}},
              {"object":"page","id":"prow2","parent":{"type":"data_source_id","data_source_id":"ds1"},
               "properties":{"t":{"type":"title","title":[{"plain_text":"Check the box to mark items as done"}]}}}
            ],"has_more":false}
        """.trimIndent()
        val transport = FakeTransport().on(HttpMethod.POST, "/search", body = searchResult)
        val service = OnboardingService(client(transport), FakeMappingStore())

        val pages = service.listGrantedPages()

        assertEquals(listOf("pworkspace", "psub"), pages.map { it.pageId })
        assertTrue(pages.none { it.title.contains("lease") || it.title.contains("Check the box") })
    }

    @Test fun `byo completes only when required roles are satisfied`() = runTest {
        val transport = FakeTransport().on(HttpMethod.GET, "/data_sources/ds1", body = dataSourceSchema)
        val store = FakeMappingStore()
        val service = OnboardingService(client(transport), store)
        val schema = service.loadSchema("ds1")
        val suggestion = service.suggestMapping(schema)

        val ok = service.completeByo("ws1", schema, suggestion.bindings, suggestion.priorities, suggestion.buckets, "My board")
        assertTrue(ok is OnboardingService.CompleteResult.Success)
        assertEquals("mapping-ds1", store.load().active?.id)

        val missing = service.completeByo("ws1", schema, emptyList(), emptyList(), emptyList(), "x")
        assertTrue(missing is OnboardingService.CompleteResult.Invalid)
    }
}

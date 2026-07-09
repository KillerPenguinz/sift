package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceEngineDmfs
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.core.notion.Backoff
import com.ironclinicgym.sift.core.notion.FakeTransport
import com.ironclinicgym.sift.core.notion.HttpMethod
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.RateLimiter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWriteServiceTest {

    private var idCounter = 0

    private fun client(transport: FakeTransport) = NotionClient(
        transport = transport,
        authTokenProvider = { "token" },
        rateLimiter = RateLimiter(nowMs = { 0L }, sleep = {}),
        backoff = Backoff(sleep = {}),
        createBackoff = Backoff(retryAmbiguous = false, sleep = {}),
    )

    private fun service(
        transport: FakeTransport,
        cache: FakeTaskCache = FakeTaskCache(),
        local: FakeLocalStateStore = FakeLocalStateStore(),
        mapping: com.ironclinicgym.sift.core.mapping.DatabaseMapping = mappingFixture(),
    ): TaskWriteService = TaskWriteService(
        notion = client(transport),
        mappingStore = FakeMappingStore(MappingSet(listOf(mapping), mapping.id)),
        taskCache = cache,
        localState = local,
        recurrence = RecurrenceEngineDmfs(),
        todayIso = { "2026-07-02" },
        newId = { "id-${idCounter++}" },
    )

    @Test
    fun `add writes the page and lands the task in the cache`() = runTest {
        val transport = FakeTransport().on(HttpMethod.POST, "/pages", 200, """{"id":"page-new"}""")
        val cache = FakeTaskCache()
        val result = service(transport, cache).add(
            TaskDraft(mappingId = "m1", title = "Buy milk", priorityOptionName = "asap", priorityOptionId = "asap-id"),
        )
        assertTrue(result is WriteResult.Success)
        assertEquals("page-new", (result as WriteResult.Success).pageId)
        // No pending temp row remains; the real page is cached.
        assertEquals(listOf("page-new"), cache.tasks.value.map { it.pageId })
    }

    @Test
    fun `add rolls back and preserves the draft on a validation failure`() = runTest {
        val transport = FakeTransport().on(HttpMethod.POST, "/pages", 400, """{"code":"validation_error","message":"bad"}""")
        val cache = FakeTaskCache()
        val draft = TaskDraft(mappingId = "m1", title = "Buy milk")
        val result = service(transport, cache).add(draft)
        assertTrue(result is WriteResult.Failure)
        assertEquals(draft, (result as WriteResult.Failure).recoverableDraft)
        assertTrue(cache.tasks.value.isEmpty()) // optimistic temp removed
    }

    @Test
    fun `add reconciles an ambiguous failure instead of duplicating`() = runTest {
        // Create returns 500 (ambiguous, not retried); the reconcile query finds the real page.
        val transport = FakeTransport()
            .on(HttpMethod.POST, "/pages", 500, """{"code":"internal_server_error","message":"boom"}""")
            .on(HttpMethod.POST, "/data_sources/ds1/query", 200, """{"results":[{"id":"page-real"}],"has_more":false}""")
        val cache = FakeTaskCache()
        val result = service(transport, cache).add(TaskDraft(mappingId = "m1", title = "Buy milk"))
        assertTrue(result is WriteResult.Success)
        assertEquals("page-real", (result as WriteResult.Success).pageId)
        assertEquals(listOf("page-real"), cache.tasks.value.map { it.pageId }) // adopted, not duplicated
    }

    @Test
    fun `complete marks done, records completed today, and undo reopens`() = runTest {
        val transport = FakeTransport().on(HttpMethod.PATCH, "/pages/page-1", 200, """{"id":"page-1"}""")
        val cache = FakeTaskCache().apply { tasks.value = listOf(taskFixture()) }
        val local = FakeLocalStateStore()
        val svc = service(transport, cache, local)

        val result = svc.complete("page-1")
        assertTrue(result is WriteResult.Success)
        assertTrue(cache.tasks.value.first().isDone)
        assertEquals("2026-07-02", local.states.value.first { it.pageId == "page-1" }.completedOnIso)

        val undo = (result as WriteResult.Success).undo
        assertNotNull(undo)
        undo!!.inverse()
        assertFalse(cache.tasks.value.first().isDone)
        assertNull(local.states.value.first { it.pageId == "page-1" }.completedOnIso)
    }

    @Test
    fun `completing a recurring task spawns the next occurrence`() = runTest {
        val transport = FakeTransport()
            .on(HttpMethod.PATCH, "/pages/page-1", 200, """{"id":"page-1"}""")
            .on(HttpMethod.POST, "/pages", 200, """{"id":"page-2"}""")
        val cache = FakeTaskCache().apply {
            tasks.value = listOf(taskFixture(recurrenceRule = "FREQ=DAILY", due = "2026-01-01"))
        }
        service(transport, cache).complete("page-1")
        val spawned = cache.tasks.value.firstOrNull { it.pageId == "page-2" }
        assertNotNull(spawned)
        assertEquals("2026-01-02", spawned!!.due) // next daily occurrence
    }

    @Test
    fun `remove archives and undo restores`() = runTest {
        val transport = FakeTransport().on(HttpMethod.PATCH, "/pages/page-1", 200, """{"id":"page-1"}""")
        val cache = FakeTaskCache().apply { tasks.value = listOf(taskFixture()) }
        val result = service(transport, cache).remove("page-1")
        assertTrue(result is WriteResult.Success)
        assertTrue(cache.tasks.value.isEmpty()) // optimistically gone

        (result as WriteResult.Success).undo!!.inverse()
        assertEquals(listOf("page-1"), cache.tasks.value.map { it.pageId }) // restored
    }

    @Test
    fun `move updates the priority optimistically and can be undone`() = runTest {
        val transport = FakeTransport().on(HttpMethod.PATCH, "/pages/page-1", 200, """{"id":"page-1"}""")
        val cache = FakeTaskCache().apply { tasks.value = listOf(taskFixture()) }
        val result = service(transport, cache).move("page-1", "soon", "soon-id")
        assertTrue(result is WriteResult.Success)
        assertEquals("soon-id", cache.tasks.value.first().priorityOptionId)

        (result as WriteResult.Success).undo!!.inverse()
        assertEquals("asap-id", cache.tasks.value.first().priorityOptionId) // back to original
    }

    @Test
    fun `add with recurrence on a database without the fields asks for consent`() = runTest {
        val transport = FakeTransport().on(HttpMethod.POST, "/pages", 200, """{"id":"x"}""")
        val result = service(transport, mapping = mappingFixture(withRecurrence = false))
            .add(TaskDraft(mappingId = "m1", title = "Weekly review", recurrenceRule = "FREQ=WEEKLY"))
        assertEquals(WriteResult.NeedsRecurrenceConsent, result)
    }
}

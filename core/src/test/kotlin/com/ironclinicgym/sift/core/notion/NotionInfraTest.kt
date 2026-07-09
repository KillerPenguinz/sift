package com.ironclinicgym.sift.core.notion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionInfraTest {

    @Test fun `rate limiter spaces requests by the minimum interval`() = runTest {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = RateLimiter(permitsPerSecond = 3, nowMs = { now }, sleep = { ms -> sleeps += ms; now += ms })
        repeat(4) { limiter.acquire() }
        // First proceeds immediately; the next three wait the ~333ms min interval each.
        assertEquals(listOf(333L, 333L, 333L), sleeps)
    }

    @Test fun `paginateAll follows cursors to completion`() = runTest {
        var calls = 0
        val items = paginateAll { cursor ->
            calls++
            when (cursor) {
                null -> NotionPage(listOf("a", "b"), "c1", true)
                "c1" -> NotionPage(listOf("c"), null, false)
                else -> NotionPage(emptyList(), null, false)
            }
        }
        assertEquals(listOf("a", "b", "c"), items)
        assertEquals(2, calls)
    }

    @Test fun `backoff retries rate-limited calls then succeeds`() = runTest {
        val sleeps = mutableListOf<Long>()
        val backoff = Backoff(maxAttempts = 4, sleep = { sleeps += it })
        var attempts = 0
        val result = backoff.retry {
            attempts++
            if (attempts < 3) throw NotionException.RateLimited(retryAfterSeconds = null)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(2, sleeps.size)
    }

    @Test fun `backoff never retries validation errors`() = runTest {
        val backoff = Backoff(sleep = {})
        var attempts = 0
        assertThrows(NotionException.Validation::class.java) {
            kotlinx.coroutines.runBlocking {
                backoff.retry { attempts++; throw NotionException.Validation("x", "bad") }
            }
        }
        assertEquals(1, attempts)
    }

    @Test fun `client sends explicit page_size 100, auth, and Notion-Version`() = runTest {
        val transport = FakeTransport().on(
            HttpMethod.POST, "/search",
            body = """{"results":[],"has_more":false,"next_cursor":null}""",
        )
        val client = NotionClient(transport, authTokenProvider = { "tok_123" }, rateLimiter = RateLimiter(sleep = {}))
        client.searchDataSources()

        val req = transport.requests.single()
        assertTrue(req.body!!.contains("\"page_size\":100"))
        assertEquals("Bearer tok_123", req.headers["Authorization"])
        assertEquals(NotionClient.NOTION_VERSION, req.headers["Notion-Version"])
    }

    @Test fun `client maps 401 to Unauthorized`() = runTest {
        val transport = FakeTransport().on(HttpMethod.GET, "/data_sources/", code = 401,
            body = """{"code":"unauthorized","message":"token revoked"}""")
        val client = NotionClient(transport, authTokenProvider = { "tok" }, rateLimiter = RateLimiter(sleep = {}))
        assertThrows(NotionException.Unauthorized::class.java) {
            kotlinx.coroutines.runBlocking { client.retrieveDataSource("ds1") }
        }
    }

    @Test fun `missing token short-circuits to Unauthorized`() = runTest {
        val client = NotionClient(FakeTransport(), authTokenProvider = { null }, rateLimiter = RateLimiter(sleep = {}))
        assertThrows(NotionException.Unauthorized::class.java) {
            kotlinx.coroutines.runBlocking { client.retrieveDataSource("ds1") }
        }
    }
}

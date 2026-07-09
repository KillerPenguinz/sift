package com.ironclinicgym.sift.core.notion

/**
 * A scriptable [HttpTransport] for tests. Records every request and returns a canned response
 * chosen by a matcher on (method, url). Lets us assert request shape (page_size, headers,
 * paths) and drive error paths without any real network.
 */
class FakeTransport : HttpTransport {
    private class Rule(val match: (HttpRequest) -> Boolean, val supply: () -> HttpResponse)

    val requests = mutableListOf<HttpRequest>()
    private val rules = mutableListOf<Rule>()

    /** Always answer matching requests with the same response. */
    fun on(method: HttpMethod, urlContains: String, code: Int = 200, body: String = "{}") = apply {
        rules += Rule({ it.method == method && it.url.contains(urlContains) }) { HttpResponse(code, emptyMap(), body) }
    }

    /** Answer matching requests with successive bodies (e.g. paginated pages), then stop matching. */
    fun enqueue(method: HttpMethod, urlContains: String, vararg bodies: String) = apply {
        val queue = ArrayDeque(bodies.toList())
        rules += Rule({ it.method == method && it.url.contains(urlContains) && queue.isNotEmpty() }) {
            HttpResponse(200, emptyMap(), queue.removeFirst())
        }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return rules.firstOrNull { it.match(request) }?.supply()
            ?: HttpResponse(404, emptyMap(), """{"code":"object_not_found","message":"no stub"}""")
    }
}

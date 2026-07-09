package com.ironclinicgym.sift.core.notion

/**
 * The single seam between core and a platform HTTP engine.
 *
 * Core builds requests and parses responses with no knowledge of OkHttp, Ktor, or any
 * Android type. The :app module supplies an implementation (OkHttp) via dependency
 * injection. Keeping this an interface is what lets the entire Notion client stay portable
 * into a KMP shared module.
 */
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

enum class HttpMethod { GET, POST, PATCH, DELETE }

data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(
    val code: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {
    val isSuccess: Boolean get() = code in 200..299

    /** Case-insensitive header lookup (header names are not case-stable across engines). */
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

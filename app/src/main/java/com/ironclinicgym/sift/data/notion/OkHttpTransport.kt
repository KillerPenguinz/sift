package com.ironclinicgym.sift.data.notion

import com.ironclinicgym.sift.core.notion.HttpMethod
import com.ironclinicgym.sift.core.notion.HttpRequest
import com.ironclinicgym.sift.core.notion.HttpResponse
import com.ironclinicgym.sift.core.notion.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp implementation of the core [HttpTransport] seam. This is the only place in the app
 * that knows about OkHttp; core stays engine-agnostic. Blocking calls run on the IO
 * dispatcher. IO failures propagate as exceptions, which NotionClient maps to a Network error.
 */
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : HttpTransport {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val body = request.body?.toRequestBody(jsonMedia)
        val builder = Request.Builder().url(request.url)
        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> builder.post(body ?: "".toRequestBody(jsonMedia))
            HttpMethod.PATCH -> builder.patch(body ?: "".toRequestBody(jsonMedia))
            HttpMethod.DELETE -> if (body != null) builder.delete(body) else builder.delete()
        }
        request.headers.forEach { (name, value) -> builder.addHeader(name, value) }

        client.newCall(builder.build()).execute().use { response ->
            HttpResponse(
                code = response.code,
                headers = response.headers.names().associateWith { response.headers[it].orEmpty() },
                body = response.body?.string().orEmpty(),
            )
        }
    }
}

package com.ironclinicgym.sift.core.oauth

import com.ironclinicgym.sift.core.notion.HttpMethod
import com.ironclinicgym.sift.core.notion.HttpRequest
import com.ironclinicgym.sift.core.notion.NotionParser
import com.ironclinicgym.sift.core.notion.model.NotionTokenResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds OAuth requests and parses redirects. Pure and stateless: the app stores the issued
 * [AuthorizeRequest.state] and the browser launch + redirect capture happen in the platform
 * layer. The token exchange targets Sift's proxy, which holds the client secret.
 */
class OAuthRequestFactory(
    private val config: OAuthConfig,
    private val stateGenerator: StateGenerator,
) {
    private val json = NotionParser.json

    /** Whether real OAuth values are present; when false the flow must not be launched. */
    val isConfigured: Boolean get() = config.isConfigured

    /** Build the authorize URL to open in a Custom Tab. owner=user is required by Notion. */
    fun buildAuthorizeRequest(): AuthorizeRequest {
        val state = stateGenerator.newState()
        val url = buildString {
            append(config.authorizeUrl)
            append("?client_id=").append(encode(config.clientId))
            append("&response_type=code")
            append("&owner=user")
            append("&redirect_uri=").append(encode(config.redirectUri))
            append("&state=").append(encode(state))
        }
        return AuthorizeRequest(url, state)
    }

    /** Parse the query parameters of the redirect URI into a typed result. */
    fun parseRedirect(params: Map<String, String>): AuthorizationResult {
        val error = params["error"]
        if (error != null) {
            return if (error == "access_denied") AuthorizationResult.Cancelled
            else AuthorizationResult.Failed(error, params["error_description"])
        }
        val code = params["code"] ?: return AuthorizationResult.Failed("missing_code", null)
        return AuthorizationResult.Success(code, params["state"])
    }

    /** True only when the redirect's state matches the one we issued (CSRF protection). */
    fun isStateValid(issued: String, returned: String?): Boolean =
        returned != null && returned == issued

    /**
     * Build the request to Sift's proxy. The proxy adds the client secret and forwards to
     * Notion's token endpoint, returning Notion's token JSON unchanged.
     */
    fun buildTokenExchangeRequest(code: String): HttpRequest {
        val body = buildJsonObject {
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", config.redirectUri)
        }
        return HttpRequest(
            method = HttpMethod.POST,
            url = config.tokenExchangeUrl,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body),
        )
    }

    /** Map the proxy/Notion token JSON into the bundle Sift stores. */
    fun parseTokenResponse(responseBody: String): TokenBundle {
        val token = json.decodeFromString(NotionTokenResponse.serializer(), responseBody)
        return TokenBundle(
            accessToken = token.accessToken,
            workspaceId = token.workspaceId,
            workspaceName = token.workspaceName,
            botId = token.botId,
        )
    }

    // Minimal percent-encoding for URL query values; pure Kotlin, no java.net dependency.
    private fun encode(value: String): String = buildString {
        for (b in value.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            when (c.toChar()) {
                in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> append(c.toChar())
                else -> append('%').append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

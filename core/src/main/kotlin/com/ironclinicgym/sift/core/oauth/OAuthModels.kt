package com.ironclinicgym.sift.core.oauth

import kotlinx.serialization.Serializable

/** Generates the opaque OAuth `state`. The platform layer supplies a secure implementation. */
fun interface StateGenerator {
    fun newState(): String
}

/** The authorize request to launch in a browser, plus the state to remember for verification. */
data class AuthorizeRequest(val url: String, val state: String)

/** The parsed result of the OAuth redirect back into the app. */
sealed interface AuthorizationResult {
    data class Success(val code: String, val state: String?) : AuthorizationResult
    /** User dismissed Notion's page picker / consent screen. */
    data object Cancelled : AuthorizationResult
    /** Notion returned an error redirect (for example access_denied). */
    data class Failed(val error: String, val description: String?) : AuthorizationResult
}

/**
 * The credentials Sift persists in secure storage after a successful exchange.
 * Workspace fields come from the token response, so no user-information capability is needed.
 */
@Serializable
data class TokenBundle(
    val accessToken: String,
    val workspaceId: String? = null,
    val workspaceName: String? = null,
    val botId: String? = null,
)

class OAuthException(message: String) : Exception(message)

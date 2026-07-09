package com.ironclinicgym.sift.core.oauth

/**
 * OAuth configuration for the Notion public connection.
 *
 * Notion capabilities (Read content, Insert content, Update content, and nothing else) are
 * configured on the integration in Notion's dashboard, not passed as request-time scopes.
 * The authorize request only carries client id, redirect, response type, owner, and state.
 *
 * Per the locked decision, the token exchange does NOT happen in the app: the app sends the
 * authorization code to a small backend proxy ([tokenExchangeUrl]) that holds the client
 * secret and calls Notion. The client secret must never ship in the APK.
 */
data class OAuthConfig(
    val clientId: String,
    val redirectUri: String,
    /** Sift's token-exchange proxy endpoint. Holds the client secret server-side. */
    val tokenExchangeUrl: String,
    val authorizeUrl: String = "https://api.notion.com/v1/oauth/authorize",
) {
    /**
     * True only when real values have been supplied. When false, the app must not launch the
     * authorize URL (it would be a broken link with a placeholder client id) and should tell
     * the user OAuth is not set up yet.
     */
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientId != PLACEHOLDER_CLIENT_ID &&
            tokenExchangeUrl.isNotBlank() && tokenExchangeUrl != PLACEHOLDER_PROXY_URL

    companion object {
        // TODO(BJ): supply the real values from the Notion integration + deployed proxy.
        // Placeholders so the module compiles; injected for real via the platform DI layer.
        const val REDIRECT_URI = "sift://oauth"
        const val PLACEHOLDER_CLIENT_ID = "REPLACE_WITH_NOTION_CLIENT_ID"
        const val PLACEHOLDER_PROXY_URL = "https://REPLACE_WITH_SIFT_PROXY/oauth/token"
    }
}

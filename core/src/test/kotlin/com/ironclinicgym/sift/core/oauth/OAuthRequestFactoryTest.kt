package com.ironclinicgym.sift.core.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthRequestFactoryTest {

    private val config = OAuthConfig(
        clientId = "client abc",
        redirectUri = "sift://oauth",
        tokenExchangeUrl = "https://proxy.example/oauth/token",
    )
    private val factory = OAuthRequestFactory(config) { "state-123" }

    @Test fun `authorize url carries client id, owner user, redirect, and state`() {
        val req = factory.buildAuthorizeRequest()
        assertTrue(req.url.startsWith(config.authorizeUrl))
        assertTrue(req.url.contains("client_id=client%20abc"))
        assertTrue(req.url.contains("owner=user"))
        assertTrue(req.url.contains("response_type=code"))
        assertTrue(req.url.contains("redirect_uri=sift%3A%2F%2Foauth"))
        assertTrue(req.url.contains("state=state-123"))
        assertEquals("state-123", req.state)
    }

    @Test fun `redirect with code parses to success`() {
        val result = factory.parseRedirect(mapOf("code" to "abc", "state" to "state-123"))
        assertTrue(result is AuthorizationResult.Success)
        result as AuthorizationResult.Success
        assertEquals("abc", result.code)
        assertTrue(factory.isStateValid("state-123", result.state))
    }

    @Test fun `access denied parses to cancelled`() {
        val result = factory.parseRedirect(mapOf("error" to "access_denied"))
        assertTrue(result is AuthorizationResult.Cancelled)
    }

    @Test fun `other errors parse to failed`() {
        val result = factory.parseRedirect(mapOf("error" to "server_error", "error_description" to "boom"))
        assertTrue(result is AuthorizationResult.Failed)
    }

    @Test fun `state mismatch is rejected`() {
        assertFalse(factory.isStateValid("state-123", "different"))
        assertFalse(factory.isStateValid("state-123", null))
    }

    @Test fun `token exchange request targets the proxy with authorization_code grant`() {
        val req = factory.buildTokenExchangeRequest("the-code")
        assertEquals(config.tokenExchangeUrl, req.url)
        assertTrue(req.body!!.contains("\"grant_type\":\"authorization_code\""))
        assertTrue(req.body!!.contains("\"code\":\"the-code\""))
    }

    @Test fun `token response parses workspace fields without a user lookup`() {
        val bundle = factory.parseTokenResponse(
            """{"access_token":"tok","workspace_id":"ws","workspace_name":"My Space","bot_id":"bot"}""",
        )
        assertEquals("tok", bundle.accessToken)
        assertEquals("My Space", bundle.workspaceName)
    }
}

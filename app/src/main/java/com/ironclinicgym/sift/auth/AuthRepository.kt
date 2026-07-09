package com.ironclinicgym.sift.auth

import com.ironclinicgym.sift.core.domain.ports.TokenStore
import com.ironclinicgym.sift.core.notion.HttpTransport
import com.ironclinicgym.sift.core.oauth.AuthorizationResult
import com.ironclinicgym.sift.core.oauth.OAuthRequestFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-side OAuth orchestration. Builds the authorize URL (remembering the issued state),
 * and handles the redirect: validate state, exchange the code through Sift's proxy, persist
 * the token. Exposes [state] so the running UI reacts to a redirect captured by a separate
 * Activity. Pure OAuth logic stays in core; this is just the platform wiring + storage.
 */
class AuthRepository(
    private val factory: OAuthRequestFactory,
    private val transport: HttpTransport,
    private val tokenStore: TokenStore,
) {
    sealed interface AuthState {
        data object Idle : AuthState
        data object InProgress : AuthState
        data class Connected(val workspaceName: String?) : AuthState
        data object Cancelled : AuthState
        data class Error(val message: String) : AuthState
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var pendingState: String? = null

    /** False until a real client id + proxy URL are configured (see OAuthConfig). */
    val isOAuthConfigured: Boolean get() = factory.isConfigured

    /**
     * Build the authorize URL and remember the state for CSRF verification on redirect.
     * Returns null (and does not launch) when OAuth is not configured yet, so the UI can show
     * a clear message instead of opening a broken link.
     */
    fun beginAuthorization(): String? {
        if (!isOAuthConfigured) return null
        val request = factory.buildAuthorizeRequest()
        pendingState = request.state
        _state.value = AuthState.InProgress
        return request.url
    }

    suspend fun handleRedirect(params: Map<String, String>) {
        when (val result = factory.parseRedirect(params)) {
            is AuthorizationResult.Cancelled -> _state.value = AuthState.Cancelled
            is AuthorizationResult.Failed ->
                _state.value = AuthState.Error("Notion could not connect. Please try again.")
            is AuthorizationResult.Success -> exchange(result)
        }
    }

    private suspend fun exchange(result: AuthorizationResult.Success) {
        if (!factory.isStateValid(pendingState.orEmpty(), result.state)) {
            _state.value = AuthState.Error("The sign in could not be verified. Please try again.")
            return
        }
        try {
            val response = transport.execute(factory.buildTokenExchangeRequest(result.code))
            if (response.code !in 200..299) {
                _state.value = AuthState.Error("Sift could not finish connecting. Please try again.")
                return
            }
            val bundle = factory.parseTokenResponse(response.body)
            tokenStore.save(bundle)
            _state.value = AuthState.Connected(bundle.workspaceName)
        } catch (e: Exception) {
            _state.value = AuthState.Error("Sift could not reach Notion. Check your connection.")
        }
    }

    suspend fun isConnected(): Boolean = tokenStore.load() != null

    fun reset() {
        pendingState = null
        _state.value = AuthState.Idle
    }
}

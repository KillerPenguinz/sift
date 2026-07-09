package com.ironclinicgym.sift.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironclinicgym.sift.auth.AuthRepository
import com.ironclinicgym.sift.auth.OAuthLauncher
import com.ironclinicgym.sift.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings logic: expose the persisted mapping and the reconnect action. Re-pointing or
 * re-mapping the board is done by running the byo flow again, which overwrites the active mapping
 * without redoing the rest of onboarding.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val mappingSet = container.repository.mappingSet

    val themeMode: StateFlow<String> = container.appPreferences.themeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "auto")

    fun setThemeMode(mode: String) {
        viewModelScope.launch { container.appPreferences.setThemeMode(mode) }
    }

    /** False until real OAuth values are configured. */
    val isOAuthConfigured: Boolean get() = container.authRepository.isOAuthConfigured

    val authState = container.authRepository.state

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    suspend fun workspaceName(): String? = container.repository.workspaceName()

    /**
     * Launch OAuth in a Custom Tab to get a fresh token. Does NOT disconnect first, so if
     * the user cancels the old token still works. The new token overwrites the old one on
     * success, which also picks up any databases the user grants this time.
     */
    fun reconnect(context: Context) {
        if (!isOAuthConfigured) {
            _notice.value = "Sign in with Notion is not set up in this build yet. Add the Notion " +
                "client id and proxy URL to enable reconnect. Your dev token still works for now."
            return
        }
        val url = container.authRepository.beginAuthorization() ?: return
        OAuthLauncher.launch(context, url)
    }

    fun clearReconnectState() {
        container.authRepository.reset()
        _notice.value = null
    }

    /** Debug tool: wipe all local state to a fresh new-user install, then re-run the gate. */
    fun resetToNewUser(onDone: () -> Unit) {
        viewModelScope.launch {
            container.resetToNewUserState()
            onDone()
        }
    }
}

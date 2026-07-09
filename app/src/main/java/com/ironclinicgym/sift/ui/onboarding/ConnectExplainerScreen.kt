package com.ironclinicgym.sift.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.auth.AuthRepository
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftPrimaryButton
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.SiftWordmark

/**
 * First screen a new user sees: explains what Sift does and connects to Notion via OAuth.
 * Combines the welcome branding with the connect action so the user lands on a single,
 * actionable screen instead of tapping through an intermediate page.
 */
@Composable
fun ConnectScreen(viewModel: OnboardingViewModel, onConnected: () -> Unit) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthRepository.AuthState.Connected) {
            viewModel.resetAuth()
            onConnected()
        }
    }

    SiftScreen {
        Spacer(Modifier.height(48.dp))
        SiftWordmark()
        SiftHeading("Everything sorted. Nothing buried.")
        SiftBody(
            "Sift connects to your Notion workspace to organize your tasks into a calm, " +
                "priority board. Notion will ask which pages to share with Sift.",
        )
        Spacer(Modifier.height(24.dp))

        when (val state = authState) {
            is AuthRepository.AuthState.Error -> SiftBody(state.message)
            AuthRepository.AuthState.Cancelled -> SiftBody("Connection was cancelled. You can try again.")
            else -> Unit
        }

        if (!viewModel.isOAuthConfigured) {
            SiftBody(
                "Sign in with Notion is not set up in this build. Add the Notion client id and " +
                    "proxy URL in local.properties to enable it.",
            )
        }

        SiftPrimaryButton(
            text = when (authState) {
                is AuthRepository.AuthState.InProgress -> "Waiting for Notion"
                else -> "Connect to Notion"
            },
            enabled = viewModel.isOAuthConfigured && authState !is AuthRepository.AuthState.InProgress,
            onClick = { viewModel.beginConnect(context) },
        )
    }
}

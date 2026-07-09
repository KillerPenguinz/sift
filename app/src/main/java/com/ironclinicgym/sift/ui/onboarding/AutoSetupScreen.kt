package com.ironclinicgym.sift.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftPrimaryButton
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.SiftSecondaryButton

/**
 * Automatic setup. Kicks off provisioning on entry and reports progress. On success the
 * mapping is already persisted; the user continues to the board. On failure the error is
 * actionable and retryable, never a dead state.
 */
@Composable
fun AutoSetupScreen(viewModel: OnboardingViewModel, onReady: () -> Unit) {
    val state by viewModel.autoSetup.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.prepareAutoSetup() }

    SiftScreen {
        SiftHeading("Setting up your board")
        when (val s = state) {
            OnboardingViewModel.AutoSetupUi.Idle,
            OnboardingViewModel.AutoSetupUi.Preparing,
            OnboardingViewModel.AutoSetupUi.Working -> {
                SiftBody("Creating your task database and a couple of examples. This takes a moment.")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            is OnboardingViewModel.AutoSetupUi.ChoosePage -> {
                SiftBody("You granted Sift more than one page. Choose where your board should live.")
                Spacer(Modifier.height(8.dp))
                s.pages.forEach { page ->
                    SiftSecondaryButton(page.title, onClick = { viewModel.choosePage(page.pageId) })
                }
            }
            is OnboardingViewModel.AutoSetupUi.Done -> {
                SiftBody("Finishing up...")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
                LaunchedEffect(Unit) { onReady() }
            }
            is OnboardingViewModel.AutoSetupUi.Error -> {
                SiftBody(s.message)
                Spacer(Modifier.height(16.dp))
                SiftPrimaryButton("Try again", onClick = { viewModel.prepareAutoSetup() })
            }
        }
    }
}

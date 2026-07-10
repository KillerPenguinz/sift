package com.ironclinicgym.sift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ironclinicgym.sift.SiftApp
import com.ironclinicgym.sift.ui.board.BoardViewModel
import com.ironclinicgym.sift.ui.board.MaterialSymbol
import com.ironclinicgym.sift.ui.board.SiftPagerScreen
import com.ironclinicgym.sift.ui.board.CustomizeBoardScreen
import com.ironclinicgym.sift.ui.board.FocusedPriorityScreen
import com.ironclinicgym.sift.ui.board.MenuHubScreen
import com.ironclinicgym.sift.ui.common.appViewModel
import com.ironclinicgym.sift.ui.onboarding.AutoSetupScreen
import com.ironclinicgym.sift.ui.onboarding.ByoDatabasePickerScreen
import com.ironclinicgym.sift.ui.onboarding.ByoMappingScreen
import com.ironclinicgym.sift.ui.onboarding.ConnectScreen
import com.ironclinicgym.sift.ui.onboarding.OnboardingViewModel
import com.ironclinicgym.sift.ui.onboarding.SetupChoiceScreen
import com.ironclinicgym.sift.ui.settings.DeveloperScreen
import com.ironclinicgym.sift.ui.settings.SettingsScreen
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * The Phase 1 navigation graph. A single [OnboardingViewModel] is hoisted here (Activity
 * scoped) so the bring your own database pick and map steps share state. The splash
 * destination gates the start: ready board if onboarded, setup choice if connected,
 * otherwise the welcome.
 */
@Composable
fun SiftNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val container = (context.applicationContext as SiftApp).container
    val onboardingVm = appViewModel { OnboardingViewModel(it) }
    val boardVm = appViewModel { BoardViewModel(it) }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            var onboarded by remember { mutableStateOf<Boolean?>(null) }
            var connected by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                onboarded = container.repository.isOnboardedNow()
                connected = container.authRepository.isConnected()
            }
            val ob = onboarded
            val cn = connected
            LaunchedEffect(ob, cn) {
                if (ob == null || cn == null) return@LaunchedEffect
                val target = when {
                    ob -> Routes.BOARD
                    cn -> Routes.SETUP_CHOICE
                    else -> Routes.CONNECT
                }
                navController.navigate(target) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        composable(Routes.CONNECT) {
            ConnectScreen(
                viewModel = onboardingVm,
                onConnected = {
                    navController.navigate(Routes.SETUP_CHOICE) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETUP_CHOICE) {
            SetupChoiceScreen(
                onAutoSetup = { navController.navigate(Routes.AUTO_SETUP) },
                onByo = { navController.navigate(Routes.BYO_PICK) },
            )
        }

        composable(Routes.AUTO_SETUP) {
            AutoSetupScreen(
                viewModel = onboardingVm,
                onReady = {
                    navController.navigate("${Routes.BOARD}?${Routes.BOARD_ARG_FROM_SETUP}=true") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.BYO_PICK) {
            ByoDatabasePickerScreen(
                viewModel = onboardingVm,
                onPicked = { navController.navigate(Routes.BYO_MAP) },
            )
        }

        composable(Routes.BYO_MAP) {
            ByoMappingScreen(
                viewModel = onboardingVm,
                onMapped = {
                    navController.navigate("${Routes.BOARD}?${Routes.BOARD_ARG_FROM_SETUP}=true") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "${Routes.BOARD}?${Routes.BOARD_ARG_FROM_SETUP}={${Routes.BOARD_ARG_FROM_SETUP}}",
            arguments = listOf(navArgument(Routes.BOARD_ARG_FROM_SETUP) { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val fromSetup = entry.arguments?.getBoolean(Routes.BOARD_ARG_FROM_SETUP) == true
            SiftPagerScreen(
                viewModel = boardVm,
                fromSetup = fromSetup,
                onOpenPriority = { priorityId -> navController.navigate("${Routes.FOCUSED_PRIORITY}/${android.net.Uri.encode(priorityId)}") },
                onOpenMenu = { navController.navigate(Routes.MENU_HUB) },
            )
        }

        composable(
            route = "${Routes.FOCUSED_PRIORITY}/{${Routes.FOCUSED_PRIORITY_ARG}}",
            arguments = listOf(navArgument(Routes.FOCUSED_PRIORITY_ARG) { type = NavType.StringType }),
            enterTransition = { scaleIn(tween(280), initialScale = 0.92f) + fadeIn(tween(200)) },
            popExitTransition = { scaleOut(tween(240), targetScale = 0.92f) + fadeOut(tween(200)) },
        ) { entry ->
            val priorityId = android.net.Uri.decode(entry.arguments?.getString(Routes.FOCUSED_PRIORITY_ARG).orEmpty())
            FocusedPriorityScreen(viewModel = boardVm, priorityId = priorityId, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                onRemap = { navController.navigate(Routes.BYO_PICK) },
                onDeveloper = { navController.navigate(Routes.DEVELOPER) },
                onResetApp = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                boardVm = boardVm,
            )
        }

        composable(Routes.CUSTOMIZE) {
            CustomizeBoardScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DEVELOPER) {
            DeveloperScreen(
                onBack = { navController.popBackStack() },
                onReset = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MENU_HUB) {
            LaunchedEffect(Unit) { boardVm.markNotificationsRead() }
            MenuHubScreen(
                viewModel = boardVm,
                onNotifications = { navController.navigate(Routes.NOTIFICATION_CENTER) },
                onActionHistory = { navController.navigate(Routes.ACTION_HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }

        // TODO(Task 5): Replace with NotificationCenterScreen (docs/superpowers/plans/2026-07-09-phase-3-5-round-2.md).
        composable(Routes.NOTIFICATION_CENTER) {
            ComingSoonScreen(title = "Notifications", onBack = { navController.popBackStack() })
        }

        // TODO(Task 5): Replace with ActionHistoryScreen (docs/superpowers/plans/2026-07-09-phase-3-5-round-2.md).
        composable(Routes.ACTION_HISTORY) {
            ComingSoonScreen(title = "Action History", onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Temporary placeholder for [Routes.NOTIFICATION_CENTER] and [Routes.ACTION_HISTORY].
 * TODO(Task 5): Remove once NotificationCenterScreen and ActionHistoryScreen exist
 * (docs/superpowers/plans/2026-07-09-phase-3-5-round-2.md).
 */
@Composable
private fun ComingSoonScreen(title: String, onBack: () -> Unit) {
    val tokens = SiftTheme.tokens
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .background(tokens.neutrals.bg.toColor())
            .padding(top = topInset)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol("arrow_back", tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = false)
            }
            Text(
                title,
                color = tokens.neutrals.textPrimary.toColor(),
                fontFamily = Bricolage,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Coming soon.", color = tokens.neutrals.textTertiary.toColor())
        }
    }
}

package com.ironclinicgym.sift.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ironclinicgym.sift.auth.AuthRepository
import com.ironclinicgym.sift.core.board.Landmark
import com.ironclinicgym.sift.core.board.OneDayLandmarks
import com.ironclinicgym.sift.ui.board.BoardViewModel
import com.ironclinicgym.sift.ui.board.CustomizeViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.ironclinicgym.sift.BuildConfig
import com.ironclinicgym.sift.core.domain.SupportUrls
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.ui.board.MaterialSymbol
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.appViewModel
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * Redesigned settings: grouped, scannable sections that scale as the app grows. Technical and
 * developer bits live in their own areas; unbuilt features are shown with a "coming soon" pill
 * so nothing feels mysteriously missing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCustomize: () -> Unit,
    onRemap: () -> Unit,
    onDeveloper: () -> Unit,
    onResetApp: () -> Unit,
    boardVm: BoardViewModel,
) {
    val viewModel = appViewModel { SettingsViewModel(it) }
    val customizeVm = appViewModel { CustomizeViewModel(it) }
    val mappingSet by viewModel.mappingSet.collectAsStateWithLifecycle(initialValue = MappingSet.EMPTY)
    val boardSettings by customizeVm.settings.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val workspace by produceState<String?>(initialValue = null) { value = viewModel.workspaceName() }
    val tokens = SiftTheme.tokens
    val active = mappingSet.active
    val use24 = boardSettings?.use24HourTime == true
    val context = LocalContext.current
    var devUnlocked by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val reconnectSubtitle = when (authState) {
        is AuthRepository.AuthState.InProgress -> "Waiting for Notion"
        is AuthRepository.AuthState.Connected -> "Reconnected"
        is AuthRepository.AuthState.Error -> (authState as AuthRepository.AuthState.Error).message
        is AuthRepository.AuthState.Cancelled -> "Cancelled"
        else -> notice
    }

    LaunchedEffect(authState) {
        if (authState is AuthRepository.AuthState.Connected ||
            authState is AuthRepository.AuthState.Cancelled
        ) {
            viewModel.clearReconnectState()
        }
    }

    SiftScreen {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                MaterialSymbol("arrow_back", tokens.neutrals.textPrimary.toColor(), size = 22.sp)
            }
            SiftHeading("Settings")
        }

        SettingsGroup("Board") {
            SettingsRow(
                title = "Customize board",
                subtitle = "Priorities, buckets, and schedules",
                icon = "tune",
                showDivider = false,
                onClick = onCustomize,
            )
        }

        SettingsGroup("Display") {
            SettingsRow(
                title = "Use 24 hour time",
                subtitle = if (use24) "Times show as 14:30" else "Times show as 2:30 PM",
                icon = "schedule",
                showDivider = false,
                onClick = null,
                trailing = {
                    Switch(checked = use24, onCheckedChange = { customizeVm.setUse24HourTime(it) })
                },
            )
        }

        val oneDayEnabled = boardSettings?.oneDayLandmarksEnabled ?: emptySet()
        SettingsGroup("One day") {
            Landmark.entries.forEachIndexed { index, landmark ->
                val name = landmark.name
                SettingsRow(
                    title = OneDayLandmarks.displayLabel(landmark),
                    icon = "calendar_today",
                    showDivider = index < Landmark.entries.lastIndex,
                    onClick = null,
                    trailing = {
                        Switch(
                            checked = name in oneDayEnabled,
                            onCheckedChange = { customizeVm.setOneDayLandmarkEnabled(name, it) },
                        )
                    },
                )
            }
        }

        val inflationEnabled = boardSettings?.signalInflationEnabled != false
        SettingsGroup("Signal Inflation") {
            SettingsRow(
                title = "Enable nudges",
                subtitle = if (inflationEnabled) "Notifies you when ASAP or Protected gets crowded" else "Nudges are off",
                icon = "notifications_active",
                showDivider = inflationEnabled,
                onClick = null,
                trailing = {
                    Switch(
                        checked = inflationEnabled,
                        onCheckedChange = { boardVm.onSignalInflationMasterToggle(it) },
                    )
                },
            )
            if (inflationEnabled) {
                SettingsNumberRow(
                    title = "ASAP threshold",
                    subtitle = "Nudge when ASAP has this many tasks",
                    icon = "priority_high",
                    value = boardSettings?.asapInflationThreshold ?: 5,
                    showDivider = true,
                    onValueChange = { n -> customizeVm.setAsapInflationThreshold(n) },
                )
                SettingsNumberRow(
                    title = "Protected threshold (%)",
                    subtitle = "Nudge when this percent of tasks are Protected",
                    icon = "shield",
                    value = boardSettings?.protectedInflationPercent ?: 30,
                    showDivider = false,
                    onValueChange = { n -> customizeVm.setProtectedInflationPercent(n) },
                )
            }
        }

        SettingsGroup("Your Notion") {
            SettingsRow(
                title = "Connected workspace",
                subtitle = workspace ?: "Notion",
                icon = "link",
                onClick = null,
                trailing = {},
            )
            SettingsRow(
                title = "Change or remap database",
                subtitle = active?.label,
                icon = "swap_horiz",
                onClick = onRemap,
            )
            SettingsRow(
                title = "Reconnect Notion",
                subtitle = reconnectSubtitle,
                icon = "refresh",
                showDivider = false,
                onClick = { viewModel.reconnect(context) },
            )
        }

        SettingsGroup("Appearance") {
            SettingsRow(
                title = "Light",
                icon = "light_mode",
                showDivider = true,
                onClick = { viewModel.setThemeMode("light") },
                trailing = { ThemeModeRadio(selected = themeMode == "light") },
            )
            SettingsRow(
                title = "Dark",
                icon = "dark_mode",
                showDivider = true,
                onClick = { viewModel.setThemeMode("dark") },
                trailing = { ThemeModeRadio(selected = themeMode == "dark") },
            )
            SettingsRow(
                title = "System default",
                icon = "brightness_auto",
                showDivider = false,
                onClick = { viewModel.setThemeMode("auto") },
                trailing = { ThemeModeRadio(selected = themeMode == "auto") },
            )
        }

        SettingsGroup("Help and support") {
            SettingsRow(
                title = "Notion email reminders",
                subtitle = "How to turn off Notion's own due date emails",
                icon = "notifications",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUrls.NOTION_NOTIFICATIONS))) },
            )
            SettingsRow(
                title = "Support site",
                subtitle = "Setup guides, tips, and FAQs",
                icon = "help",
                showDivider = false,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUrls.ROOT))) },
            )
        }

        SettingsGroup("Coming soon") {
            ComingSoonRow("Swipe actions", "swap_horiz")
            ComingSoonRow("Themes", "palette")
            ComingSoonRow("Home screen widget", "widgets")
            ComingSoonRow("AI assist", "smart_toy", showDivider = false)
        }

        SettingsGroup("Account") {
            SettingsRow(
                title = "Reset app",
                subtitle = "Clear your token and all local data, then reconnect",
                icon = "restart_alt",
                showDivider = false,
                onClick = { showResetConfirm = true },
            )
        }

        if (devUnlocked) {
            SettingsGroup("Developer") {
                SettingsRow(
                    title = "Developer tools",
                    subtitle = "Sample data, data source details",
                    icon = "science",
                    showDivider = false,
                    onClick = onDeveloper,
                )
            }
        }

        SettingsGroup("About") {
            SettingsRow(
                title = "Sift",
                subtitle = "Version ${BuildConfig.VERSION_NAME}. Everything sorted. Nothing buried.",
                icon = "info",
                showDivider = false,
                onClick = null,
                trailing = {},
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { devUnlocked = true },
                ),
            )
        }

        Spacer(Modifier.height(16.dp))
        SiftBody("Removing a priority or task in Sift never changes your Notion data.")

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset Sift?") },
                text = { Text("This will sign you out and clear all local data. Your Notion data is not affected.") },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        viewModel.resetToNewUser { onResetApp() }
                    }) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ComingSoonRow(title: String, icon: String, showDivider: Boolean = true) {
    SettingsRow(
        title = title,
        icon = icon,
        enabled = false,
        showDivider = showDivider,
        onClick = null,
        trailing = { ComingSoonPill() },
    )
}

@Composable
private fun ThemeModeRadio(selected: Boolean) {
    val tokens = SiftTheme.tokens
    val color = if (selected) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.textTertiary.toColor()
    MaterialSymbol(if (selected) "radio_button_checked" else "radio_button_unchecked", color, size = 22.sp)
}

@Composable
private fun SettingsNumberRow(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    value: Int,
    showDivider: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        showDivider = showDivider,
        onClick = null,
        trailing = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toIntOrNull()?.let { onValueChange(it) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.25f),
            )
        },
    )
}

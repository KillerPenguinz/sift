package com.ironclinicgym.sift.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.ui.board.CustomizeViewModel
import com.ironclinicgym.sift.ui.board.MaterialSymbol
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.common.appViewModel
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

/**
 * Developer tools, grouped away from everyday settings (debug builds only): the sample-data
 * overlay and read-only data source details. A place to add future dev utilities.
 */
@Composable
fun DeveloperScreen(onBack: () -> Unit, onReset: () -> Unit) {
    val vm = appViewModel { CustomizeViewModel(it) }
    val settingsVm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mapping by vm.mapping.collectAsStateWithLifecycle()
    val tokens = SiftTheme.tokens
    var showResetConfirm by remember { mutableStateOf(false) }

    SiftScreen {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                MaterialSymbol("arrow_back", tokens.neutrals.textPrimary.toColor(), size = 22.sp)
            }
            SiftHeading("Developer tools")
        }

        SettingsGroup("Testing") {
            SettingsRow(
                title = "Show sample tasks",
                subtitle = "Fills every priority with varied counts to test the layouts. Your Notion data is not changed.",
                icon = "science",
                showDivider = false,
                onClick = null,
                trailing = { Switch(checked = settings?.sampleMode == true, onCheckedChange = { vm.setSampleMode(it) }) },
            )
        }

        SettingsGroup("Data source") {
            SettingsRow(title = "Board name", subtitle = mapping?.label ?: "Not mapped", icon = "storage", onClick = null, trailing = {})
            SettingsRow(title = "Data source id", subtitle = mapping?.ref?.dataSourceId ?: "None", icon = "link", onClick = null, trailing = {})
            SettingsRow(title = "Workspace id", subtitle = mapping?.ref?.workspaceId ?: "None", onClick = null, trailing = {})
            SettingsRow(
                title = "Mapping",
                subtitle = "Roles mapped ${mapping?.bindings?.size ?: 0}. Priorities ${mapping?.priorities?.size ?: 0}.",
                showDivider = false,
                onClick = null,
                trailing = {},
            )
        }

        SettingsGroup("Reset") {
            SettingsRow(
                title = "Reset to new user",
                subtitle = "Clears the mapping, board settings, and cached tasks, then returns to onboarding. Your Notion data is not changed.",
                icon = "delete",
                showDivider = false,
                onClick = { showResetConfirm = true },
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to new user?", color = tokens.neutrals.textPrimary.toColor()) },
            text = {
                Text(
                    "Sift will clear its local setup (mapping, board settings, cached tasks) and start onboarding from scratch. Your tasks in Notion are left untouched.",
                    color = tokens.neutrals.textSecondary.toColor(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    settingsVm.resetToNewUser(onReset)
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
            containerColor = tokens.neutrals.surfaceRaised.toColor(),
        )
    }
}

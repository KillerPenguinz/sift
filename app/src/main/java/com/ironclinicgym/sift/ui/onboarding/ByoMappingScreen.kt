package com.ironclinicgym.sift.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.accepts
import com.ironclinicgym.sift.core.mapping.displayName
import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.ui.board.MaterialSymbol
import com.ironclinicgym.sift.ui.common.SiftBody
import com.ironclinicgym.sift.ui.common.SiftHeading
import com.ironclinicgym.sift.ui.common.SiftPrimaryButton
import com.ironclinicgym.sift.ui.common.SiftSecondaryButton
import com.ironclinicgym.sift.ui.common.SiftScreen
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun ByoMappingScreen(viewModel: OnboardingViewModel, onMapped: () -> Unit) {
    val edit by viewModel.byoEdit.collectAsStateWithLifecycle()

    val current = edit ?: run {
        SiftScreen { SiftBody("Loading the database schema...") }
        return
    }

    LaunchedEffect(current.done) { if (current.done) onMapped() }

    var customizing by remember { mutableStateOf(false) }

    SiftScreen {
        SiftHeading(if (customizing) "Map your properties" else "We found your setup")
        SiftBody(
            if (customizing) "Tell Sift which property does each job."
            else "Sift matched your database properties. Confirm or customize.",
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = current.label,
            onValueChange = viewModel::setLabel,
            label = { SiftBody("Board name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(12.dp))

        if (customizing) {
            CustomizeView(current, viewModel)
        } else {
            SuggestionView(current, viewModel)
        }

        current.validation.issues.forEach { SiftBody(it.message) }

        val unmatchedNames = current.priorities
            .filter { it.optionId in current.unmatchedPriorityIds }
            .map { it.optionName }
        if (unmatchedNames.isNotEmpty()) {
            SiftBody("These priority options will become their own priorities: ${unmatchedNames.joinToString(", ")}.")
        }
        current.error?.let { SiftBody(it) }

        Spacer(Modifier.height(16.dp))

        if (customizing) {
            SiftPrimaryButton(
                text = if (current.completing) "Saving" else "Use this database",
                enabled = current.validation.isSatisfied && !current.completing,
                onClick = viewModel::completeByo,
            )
        } else if (current.missingProperties.isEmpty()) {
            SiftPrimaryButton(
                text = when {
                    current.completing -> "Saving"
                    current.creating -> "Adding properties"
                    else -> "Looks good"
                },
                enabled = current.validation.isSatisfied && !current.completing && !current.creating,
                onClick = viewModel::completeByo,
            )
        }

        if (!customizing) {
            Spacer(Modifier.height(8.dp))
            SiftSecondaryButton(
                text = "Let me customize",
                onClick = { customizing = true },
            )
        }
    }
}

@Composable
private fun SuggestionView(edit: OnboardingViewModel.ByoEdit, viewModel: OnboardingViewModel) {
    val tokens = SiftTheme.tokens

    val matchedRequired = Role.required.filter { edit.selected[it] != null }
    val matchedOptional = Role.optional.filter { edit.selected[it] != null }

    if (matchedRequired.isNotEmpty() || matchedOptional.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = tokens.neutrals.surfaceRaised.toColor()),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (matchedRequired + matchedOptional).forEach { role ->
                    val propName = edit.schema.properties.firstOrNull { it.id == edit.selected[role] }?.name
                    SuggestionRow(roleName = role.displayName(), propertyName = propName, matched = true)
                }
            }
        }
    }

    if (edit.missingProperties.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        MissingPropertiesPrompt(edit, viewModel)
    }
}

@Composable
private fun MissingPropertiesPrompt(edit: OnboardingViewModel.ByoEdit, viewModel: OnboardingViewModel) {
    val tokens = SiftTheme.tokens
    val requiredMissing = edit.missingProperties.filter { it.required }
    val optionalMissing = edit.missingProperties.filter { !it.required }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tokens.neutrals.surfaceRaised.toColor()),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (requiredMissing.isNotEmpty()) {
                Text(
                    "Sift will add these to your database:",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.neutrals.textPrimary.toColor(),
                )
                requiredMissing.forEach { mp ->
                    MissingPropertyRow(
                        name = mp.propertyName,
                        description = mp.description,
                        checked = true,
                        required = true,
                        onToggle = {},
                    )
                }
            }

            if (optionalMissing.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Want Sift to add these too?",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.neutrals.textPrimary.toColor(),
                )
                optionalMissing.forEach { mp ->
                    MissingPropertyRow(
                        name = mp.propertyName,
                        description = mp.description,
                        checked = mp.role in edit.selectedMissing,
                        required = false,
                        onToggle = { viewModel.toggleMissingProperty(mp.role) },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    SiftPrimaryButton(
        text = when {
            edit.creating -> "Adding properties"
            edit.completing -> "Saving"
            else -> "Set up and continue"
        },
        enabled = edit.canProceed && !edit.creating && !edit.completing,
        onClick = viewModel::completeByo,
    )
}

@Composable
private fun MissingPropertyRow(
    name: String,
    description: String,
    checked: Boolean,
    required: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (required) null else { _ -> onToggle() },
            enabled = !required,
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = tokens.neutrals.textPrimary.toColor())
            Text(description, style = MaterialTheme.typography.bodySmall, color = tokens.neutrals.textSecondary.toColor())
        }
    }
}

@Composable
private fun SuggestionRow(roleName: String, propertyName: String?, matched: Boolean) {
    val tokens = SiftTheme.tokens
    val primaryColor = tokens.neutrals.textPrimary.toColor()
    val secondaryColor = tokens.neutrals.textSecondary.toColor()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(
            name = if (matched) "check_circle" else "info",
            color = if (matched) primaryColor else secondaryColor,
            size = 18.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = roleName.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = primaryColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = propertyName ?: "Needs your input",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
        )
    }
}

@Composable
private fun CustomizeView(edit: OnboardingViewModel.ByoEdit, viewModel: OnboardingViewModel) {
    val usedIds = edit.selected.values.filterNotNull().toSet()

    Role.required.forEach { role ->
        RolePicker(role = role, schema = edit.schema, selectedId = edit.selected[role], excludedIds = usedIds - setOfNotNull(edit.selected[role])) { id ->
            viewModel.setRoleProperty(role, id)
        }
    }

    val visibleOptional = Role.optional.filter { role ->
        edit.selected[role] != null || edit.schema.properties.any { role.accepts(it.type) }
    }

    if (visibleOptional.isNotEmpty()) {
        var showOptional by remember { mutableStateOf(visibleOptional.any { edit.selected[it] != null }) }

        Spacer(Modifier.height(8.dp))

        if (!showOptional) {
            SiftSecondaryButton(text = "Show optional roles", onClick = { showOptional = true })
        } else {
            AnimatedVisibility(visible = true) {
                Column {
                    visibleOptional.forEach { role ->
                        RolePicker(role = role, schema = edit.schema, selectedId = edit.selected[role], excludedIds = usedIds - setOfNotNull(edit.selected[role])) { id ->
                            viewModel.setRoleProperty(role, id)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolePicker(
    role: Role,
    schema: NotionDataSourceSchema,
    selectedId: String?,
    excludedIds: Set<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val candidates = schema.properties.filter { role.accepts(it.type) && it.id !in excludedIds }
    val selectedName = schema.properties.firstOrNull { it.id == selectedId }?.name ?: "Not set"
    val label = role.displayName() + if (role.required) " (required)" else " (optional)"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { SiftBody(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!role.required) {
                DropdownMenuItem(text = { SiftBody("Not set") }, onClick = { onSelect(null); expanded = false })
            }
            candidates.forEach { property ->
                DropdownMenuItem(
                    text = { SiftBody("${property.name}  ·  ${property.type.displayName()}") },
                    onClick = { onSelect(property.id); expanded = false },
                )
            }
        }
    }
}

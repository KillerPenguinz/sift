package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.BucketView
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.domain.RecurrenceSetupService
import com.ironclinicgym.sift.core.domain.TaskDraft
import com.ironclinicgym.sift.core.domain.TaskEdits
import com.ironclinicgym.sift.core.domain.WriteResult
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

// Quick due-date choices, resolved with the device clock. On-device voice can fill title/notes
// through the platform keyboard; nothing here is gated.
private enum class DueChoice(val label: String) { NONE("No date"), TODAY("Today"), TOMORROW("Tomorrow"), NEXT_WEEK("Next week") }

private fun DueChoice.toIso(time: Pair<Int, Int>?): String? {
    val date = when (this) {
        DueChoice.NONE -> return null
        DueChoice.TODAY -> LocalDate.now()
        DueChoice.TOMORROW -> LocalDate.now().plusDays(1)
        DueChoice.NEXT_WEEK -> LocalDate.now().plusWeeks(1)
    }
    return if (time != null) {
        "%s T%02d:%02d:00".format(date, time.first, time.second).replace(" ", "")
    } else {
        date.toString()
    }
}

// Common recurrence presets as RRULE strings. A full custom builder is a later enhancement.
private data class RepeatChoice(val label: String, val rrule: String?)
private val repeatChoices = listOf(
    RepeatChoice("Does not repeat", null),
    RepeatChoice("Every day", "FREQ=DAILY"),
    RepeatChoice("Every week", "FREQ=WEEKLY"),
    RepeatChoice("Every weekday", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
    RepeatChoice("Every month", "FREQ=MONTHLY"),
)

/**
 * The add / edit task sheet. Shows the mapped fields for fast capture (title, priority, bucket,
 * due, notes) plus recurrence. Optimistic: on success it dismisses immediately; on failure it
 * stays open with an inline error so the user's input is never lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    viewModel: BoardViewModel,
    settings: BoardSettings,
    editing: ProjectedItem?,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val existing = editing?.task

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var priorityId by remember { mutableStateOf(existing?.priorityOptionId ?: settings.priorities.firstOrNull { !it.hidden }?.optionId) }
    var bucketId by remember { mutableStateOf(existing?.bucketOptionId) }
    var due by remember { mutableStateOf(if (existing?.due != null) DueChoice.TODAY else DueChoice.NONE) }
    var time by remember {
        val parsed = existing?.due?.takeIf { "T" in it }?.let { iso ->
            val parts = iso.substringAfter("T").split(":")
            if (parts.size >= 2) parts[0].toIntOrNull()?.let { h -> parts[1].toIntOrNull()?.let { m -> h to m } } else null
        }
        mutableStateOf(parsed)
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(repeatChoices.firstOrNull { it.rrule == existing?.recurrenceRule } ?: repeatChoices.first()) }
    var selectedLabels by remember { mutableStateOf(existing?.labels ?: emptyList()) }
    val availableLabels by viewModel.availableLabels.collectAsStateWithLifecycle()

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }

    val priorities = settings.priorities.filter { !it.hidden }.sortedBy { it.order }
    val buckets = settings.buckets.filter { it.optionId != null }

    fun submit() {
        if (title.isBlank() || saving) return
        saving = true
        error = null
        scope.launch {
            val chosenPriority = priorities.firstOrNull { it.optionId == priorityId }
            val chosenBucket = buckets.firstOrNull { it.optionId == bucketId }
            val result: WriteResult = if (existing == null) {
                viewModel.addTask(
                    TaskDraft(
                        mappingId = settings.mappingId,
                        title = title.trim(),
                        priorityOptionName = chosenPriority?.optionName ?: chosenPriority?.displayName,
                        bucketOptionName = chosenBucket?.displayName,
                        dueIso = due.toIso(time),
                        notes = notes.ifBlank { null },
                        recurrenceRule = repeat.rrule,
                        priorityOptionId = chosenPriority?.optionId,
                        bucketOptionId = chosenBucket?.optionId,
                        labels = selectedLabels.ifEmpty { null },
                    ),
                )
            } else {
                viewModel.editTask(
                    existing.pageId,
                    TaskEdits(
                        title = TaskEdits.Field(title.trim()),
                        priorityOptionName = TaskEdits.Field(chosenPriority?.optionName ?: chosenPriority?.displayName),
                        bucketOptionName = TaskEdits.Field(chosenBucket?.displayName),
                        dueIso = TaskEdits.Field(due.toIso(time)),
                        notes = TaskEdits.Field(notes.ifBlank { null }),
                        recurrenceRule = TaskEdits.Field(repeat.rrule),
                        labels = TaskEdits.Field(selectedLabels),
                        priorityOptionId = chosenPriority?.optionId,
                        bucketOptionId = chosenBucket?.optionId,
                    ),
                )
            }
            saving = false
            when (result) {
                is WriteResult.Success -> onDismiss()
                is WriteResult.Failure -> error = result.message
                WriteResult.NeedsRecurrenceConsent -> showConsent = true
                else -> error = "Something went wrong. Please try again."
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = tokens.neutrals.surfaceRaised.toColor()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (existing == null) "Add a task" else "Edit task",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.neutrals.textPrimary.toColor(),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("What needs doing?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Priority")
            ChipRow {
                priorities.forEach { p ->
                    SelectChip(p.displayName, selected = p.optionId == priorityId) { priorityId = p.optionId }
                }
            }

            if (buckets.isNotEmpty()) {
                FieldLabel("Bucket")
                ChipRow {
                    SelectChip("None", selected = bucketId == null) { bucketId = null }
                    buckets.forEach { b ->
                        BucketChip(b, selected = b.optionId == bucketId) { bucketId = b.optionId }
                    }
                }
            }

            if (settings.labelsAvailable && availableLabels.isNotEmpty()) {
                FieldLabel("Labels")
                ChipRow {
                    availableLabels.forEach { label ->
                        val active = label in selectedLabels
                        SelectChip(label, selected = active) {
                            selectedLabels = if (active) selectedLabels - label else selectedLabels + label
                        }
                    }
                }
            }

            FieldLabel("Due")
            ChipRow {
                DueChoice.entries.forEach { c ->
                    SelectChip(c.label, selected = c == due) {
                        due = c
                        if (c == DueChoice.NONE) time = null
                    }
                }
                if (due != DueChoice.NONE) {
                    if (time != null) {
                        SelectChip("%02d:%02d".format(time!!.first, time!!.second), selected = true) { showTimePicker = true }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable { time = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            MaterialSymbol("close", tokens.neutrals.textTertiary.toColor(), size = 16.sp)
                        }
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
                                .background(tokens.neutrals.surface.toColor())
                                .clickable { showTimePicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MaterialSymbol("schedule", tokens.neutrals.textSecondary.toColor(), size = 18.sp)
                        }
                    }
                }
            }

            FieldLabel("Repeats")
            ChipRow {
                repeatChoices.forEach { c ->
                    SelectChip(c.label, selected = c == repeat) { repeat = c }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notes") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                label = if (saving) "Saving..." else if (existing == null) "Add task" else "Save changes",
                enabled = title.isNotBlank() && !saving,
                onClick = { submit() },
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = time,
            onConfirm = { h, m -> time = h to m; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showConsent) {
        ConsentDialog(
            message = viewModel.consentPrompt(),
            onConfirm = {
                scope.launch {
                    val ok = viewModel.grantRecurrence()
                    showConsent = false
                    if (ok) submit() else { repeat = repeatChoices.first(); error = "Recurring tasks are unavailable for this database." }
                }
            },
            onDismiss = {
                showConsent = false
                repeat = repeatChoices.first() // graceful: proceed without recurrence
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        color = SiftTheme.tokens.neutrals.textTertiary.toColor(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val bg = if (selected) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.surface.toColor()
    val fg = if (selected) tokens.neutrals.bg.toColor() else tokens.neutrals.textSecondary.toColor()
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BucketChip(bucket: BucketView, selected: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val colors = bucketColorsOf(bucket.colorKey)
    val bg = if (selected) colors.bg.toColor() else tokens.neutrals.surface.toColor()
    val borderColor = if (selected) colors.color.toColor() else tokens.neutrals.border.toColor()
    val fg = if (selected) colors.onColor.toColor() else tokens.neutrals.textSecondary.toColor()
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BucketTile(bucket.icon, colors, tile = 22)
        Text(bucket.displayName, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: Pair<Int, Int>?, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial?.first ?: 9,
        initialMinute = initial?.second ?: 0,
        is24Hour = false,
    )
    val tokens = SiftTheme.tokens
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = state) },
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val bg = if (enabled) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.border.toColor()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tokens.neutrals.bg.toColor(), fontWeight = FontWeight.SemiBold)
    }
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.BucketView
import com.ironclinicgym.sift.core.board.DateBandEngine
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.domain.TaskDraft
import com.ironclinicgym.sift.core.domain.TaskEdits
import com.ironclinicgym.sift.core.domain.WriteResult
import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.ui.theme.Hanken
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class WhenPath { DATE, RANGE, BRAIN_DUMP }
private enum class RoughRange(val label: String, val key: PriorityKey) {
    SOONISH("Soon-ish", PriorityKey.SOON),
    LATER("Later", PriorityKey.LATER),
    WHENEVER("Whenever", PriorityKey.ONEDAY),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheetV2(
    viewModel: BoardViewModel,
    settings: BoardSettings,
    editing: ProjectedItem?,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val existing = editing?.task

    val draft = if (existing == null) viewModel.pendingDraft else null
    var title by remember { mutableStateOf(draft?.title ?: existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(draft?.notes ?: existing?.notes.orEmpty()) }
    var bucketId by remember { mutableStateOf(draft?.bucketId ?: existing?.bucketOptionId) }
    var whenPath by remember { mutableStateOf<WhenPath?>(
        if (draft?.isBrainDump == true) WhenPath.BRAIN_DUMP
        else if (existing?.due != null) WhenPath.DATE
        else null
    ) }
    var didSave by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(existing?.due?.take(10)) }
    var time by remember {
        val parsed = existing?.due?.takeIf { "T" in it }?.let { iso ->
            val parts = iso.substringAfter("T").split(":")
            if (parts.size >= 2) parts[0].toIntOrNull()?.let { h -> parts[1].toIntOrNull()?.let { m -> h to m } } else null
        }
        mutableStateOf(parsed)
    }
    var roughRange by remember { mutableStateOf<RoughRange?>(null) }
    var repeat by remember { mutableStateOf(REPEAT_CHOICES.firstOrNull { it.rrule == existing?.recurrenceRule } ?: REPEAT_CHOICES.first()) }
    var selectedLabels by remember { mutableStateOf(existing?.labels ?: emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val buckets = settings.buckets.filter { it.optionId != null }
    val todayIso = LocalDate.now().toString()

    // Accent color for the input field border (today accent for the blinking cursor feel)
    val todayAccent = tokens.priorities.getOrNull(1)?.accent?.toColor()
        ?: tokens.priorities.first().accent.toColor()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (draft != null) viewModel.clearDraft()
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                title = if (title.isBlank()) spoken else "$title $spoken"
            }
        }
    }

    fun resolvedDueIso(): String? {
        if (whenPath != WhenPath.DATE || selectedDate == null) return null
        val t = time
        return if (t != null) "%sT%02d:%02d:00".format(selectedDate, t.first, t.second)
        else selectedDate
    }

    fun resolvedPriority(): Pair<String?, String?> {
        return when (whenPath) {
            WhenPath.DATE -> {
                val band = DateBandEngine.resolveBand(selectedDate, todayIso)
                if (band != null) {
                    val pv = settings.priorities.firstOrNull { it.colorKey == band.name }
                    (pv?.optionName ?: pv?.displayName) to pv?.optionId
                } else null to null
            }
            WhenPath.RANGE -> {
                val key = roughRange?.key ?: return null to null
                val pv = settings.priorities.firstOrNull { it.colorKey == key.name }
                (pv?.optionName ?: pv?.displayName) to pv?.optionId
            }
            WhenPath.BRAIN_DUMP -> null to null
            null -> {
                val first = settings.priorities.filter { !it.hidden }.minByOrNull { it.order }
                (first?.optionName ?: first?.displayName) to first?.optionId
            }
        }
    }

    fun submit() {
        if (title.isBlank() || saving) return
        saving = true
        error = null
        scope.launch {
            val chosenBucket = buckets.firstOrNull { it.optionId == bucketId }
            val (priorityName, priorityId) = resolvedPriority()
            val result: WriteResult = if (existing == null) {
                viewModel.addTask(
                    TaskDraft(
                        mappingId = settings.mappingId,
                        title = title.trim(),
                        priorityOptionName = priorityName,
                        priorityOptionId = priorityId,
                        bucketOptionName = chosenBucket?.displayName,
                        bucketOptionId = chosenBucket?.optionId,
                        dueIso = resolvedDueIso(),
                        notes = notes.ifBlank { null },
                        recurrenceRule = repeat.rrule,
                        labels = selectedLabels.ifEmpty { null },
                    ),
                )
            } else {
                viewModel.editTask(
                    existing.pageId,
                    TaskEdits(
                        title = TaskEdits.Field(title.trim()),
                        priorityOptionName = TaskEdits.Field(priorityName),
                        bucketOptionName = TaskEdits.Field(chosenBucket?.displayName),
                        dueIso = TaskEdits.Field(resolvedDueIso()),
                        notes = TaskEdits.Field(notes.ifBlank { null }),
                        recurrenceRule = TaskEdits.Field(repeat.rrule),
                        labels = TaskEdits.Field(selectedLabels),
                        priorityOptionId = priorityId,
                        bucketOptionId = chosenBucket?.optionId,
                    ),
                )
            }
            saving = false
            when (result) {
                is WriteResult.Success -> {
                    didSave = true
                    viewModel.clearDraft()
                    if (existing != null) {
                        onDismiss()
                    } else {
                        if (whenPath == WhenPath.BRAIN_DUMP) {
                            viewModel.setBrainDump(result.pageId)
                        }
                        val colorKey = settings.priorities
                            .firstOrNull { it.optionId == priorityId }?.colorKey ?: "ASAP"
                        viewModel.notifyAdded(priorityName ?: "Task", colorKey)
                        title = ""
                        notes = ""
                        onDismiss()
                    }
                }
                is WriteResult.Failure -> error = result.message
                WriteResult.NeedsRecurrenceConsent -> showConsent = true
                else -> error = "Something went wrong. Please try again."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!didSave && existing == null && title.isNotBlank()) {
                viewModel.pendingDraft = BoardViewModel.DraftState(
                    title = title,
                    notes = notes,
                    bucketId = bucketId,
                    isBrainDump = whenPath == WhenPath.BRAIN_DUMP,
                )
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            // Title input field
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(tokens.neutrals.surface.toColor())
                    .border(1.5.dp, todayAccent, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Hanken,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.neutrals.textPrimary.toColor(),
                    ),
                    cursorBrush = SolidColor(todayAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "What needs doing?",
                                color = tokens.neutrals.textTertiary.toColor(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        inner()
                    },
                )
                Spacer(Modifier.width(10.dp))
                MaterialSymbol(
                    "mic", tokens.neutrals.textTertiary.toColor(), size = 20.sp, filled = false,
                    modifier = Modifier.clickable {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        runCatching { speechLauncher.launch(intent) }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            // Task / Brain dump toggle
            if (existing == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.neutrals.surface.toColor())
                        .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                ) {
                    val isTask = whenPath != WhenPath.BRAIN_DUMP
                    ToggleSegment(
                        icon = "check_circle",
                        label = "Task",
                        selected = isTask,
                        modifier = Modifier.weight(1f),
                        onClick = { if (!isTask) whenPath = null },
                    )
                    ToggleSegment(
                        icon = "lightbulb",
                        label = "Brain dump",
                        selected = !isTask,
                        modifier = Modifier.weight(1f),
                        onClick = { whenPath = WhenPath.BRAIN_DUMP },
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            // Compact bucket chips
            if (buckets.isNotEmpty() && whenPath != WhenPath.BRAIN_DUMP) {
                FieldLabel("Bucket")
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    buckets.forEach { b ->
                        val colors = bucketColorsOf(b.colorKey)
                        val sel = b.optionId == bucketId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { bucketId = b.optionId },
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(colors.bg.toColor())
                                    .then(
                                        if (sel) Modifier.border(2.dp, colors.color.toColor(), androidx.compose.foundation.shape.CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                MaterialSymbol(b.icon, colors.onColor.toColor(), size = 18.sp, filled = true)
                            }
                            if (sel) {
                                Text(
                                    b.displayName,
                                    color = tokens.neutrals.textTertiary.toColor(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // When section
            if (whenPath != WhenPath.BRAIN_DUMP) {
                FieldLabel("When")
                Spacer(Modifier.height(10.dp))

                // Date / Time / Repeat row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasDate = whenPath == WhenPath.DATE && selectedDate != null
                    // Date button
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (hasDate) Modifier
                                    .background(todayAccent.copy(alpha = 0.16f))
                                    .border(1.5.dp, todayAccent, RoundedCornerShape(12.dp))
                                else Modifier
                                    .background(tokens.neutrals.surface.toColor())
                                    .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                            )
                            .clickable {
                                whenPath = WhenPath.DATE
                                showDatePicker = true
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MaterialSymbol(
                            "calendar_month",
                            if (hasDate) todayAccent else tokens.neutrals.textSecondary.toColor(),
                            size = 19.sp,
                            filled = hasDate,
                        )
                        Text(
                            if (hasDate) humanDate(selectedDate!!) else "Date",
                            color = if (hasDate) todayAccent else tokens.neutrals.textSecondary.toColor(),
                            fontSize = 13.5.sp,
                            fontWeight = if (hasDate) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        if (hasDate) {
                            Spacer(Modifier.weight(1f))
                            MaterialSymbol(
                                "close",
                                tokens.neutrals.textTertiary.toColor(),
                                size = 17.sp,
                                filled = false,
                                modifier = Modifier.clickable {
                                    selectedDate = null
                                    time = null
                                    whenPath = null
                                },
                            )
                        }
                    }

                    // Time button
                    val timeEnabled = hasDate
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.neutrals.surface.toColor())
                            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                            .then(if (timeEnabled) Modifier.clickable { showTimePicker = true } else Modifier)
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val timeColor = if (timeEnabled) tokens.neutrals.textSecondary.toColor()
                            else tokens.neutrals.textTertiary.toColor().copy(alpha = 0.45f)
                        MaterialSymbol("schedule", timeColor, size = 19.sp, filled = false)
                        Text(
                            if (time != null) "%02d:%02d".format(time!!.first, time!!.second) else "Time",
                            color = timeColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // Repeat button
                    val hasRepeat = repeat.rrule != null
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (hasRepeat) Modifier
                                    .background(todayAccent.copy(alpha = 0.16f))
                                    .border(1.5.dp, todayAccent, RoundedCornerShape(12.dp))
                                else Modifier
                                    .background(tokens.neutrals.surface.toColor())
                                    .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                            )
                            .clickable(enabled = hasDate(whenPath, selectedDate)) { showRepeatPicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        MaterialSymbol(
                            "repeat",
                            if (hasRepeat) todayAccent else tokens.neutrals.textSecondary.toColor(),
                            size = 20.sp,
                            filled = hasRepeat,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Quick-date chips or helper text
                if (!hasDate(whenPath, selectedDate)) {
                    val today = LocalDate.now()
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        QuickDateChip("Later today") {
                            whenPath = WhenPath.DATE
                            selectedDate = today.toString()
                        }
                        QuickDateChip("Tomorrow") {
                            whenPath = WhenPath.DATE
                            selectedDate = today.plusDays(1).toString()
                        }
                        QuickDateChip("Next week") {
                            whenPath = WhenPath.DATE
                            selectedDate = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).toString()
                        }
                        QuickDateChip("Next month") {
                            whenPath = WhenPath.DATE
                            selectedDate = today.plusMonths(1).withDayOfMonth(1).toString()
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tokens.neutrals.surface.toColor())
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(10.dp))
                                .clickable {
                                    whenPath = WhenPath.DATE
                                    showDatePicker = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            MaterialSymbol("calendar_month", tokens.neutrals.textSecondary.toColor(), size = 18.sp, filled = false)
                        }
                    }
                } else {
                    Text(
                        "Sift handles the priority. Adjust anytime.",
                        color = tokens.neutrals.textTertiary.toColor(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            if (whenPath == WhenPath.BRAIN_DUMP) {
                Text(
                    "Goes to your brain dump, not the priority board.",
                    color = tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Notes field with dashed separator
            val dashedBorderColor = tokens.neutrals.border.toColor()
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = dashedBorderColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                        )
                    }
                    .padding(top = 15.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(tokens.neutrals.surface.toColor())
                        .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(13.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    MaterialSymbol("notes", tokens.neutrals.textTertiary.toColor(), size = 19.sp, filled = false, modifier = Modifier.padding(top = 1.dp))
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        textStyle = TextStyle(
                            fontFamily = Hanken,
                            fontSize = 14.sp,
                            color = tokens.neutrals.textPrimary.toColor(),
                            lineHeight = (14 * 1.4).sp,
                        ),
                        cursorBrush = SolidColor(todayAccent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (notes.isEmpty()) {
                                Text(
                                    "Add a note...",
                                    color = tokens.neutrals.textTertiary.toColor(),
                                    fontSize = 14.sp,
                                    lineHeight = (14 * 1.4).sp,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Error
            error?.let {
                Text(it, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            // Primary button
            val buttonEnabled = title.isNotBlank() && !saving
            val buttonBg = if (buttonEnabled) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.border.toColor()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(buttonBg)
                    .clickable(enabled = buttonEnabled, onClick = { submit() })
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol("add", tokens.neutrals.bg.toColor(), size = 21.sp, filled = false)
                Spacer(Modifier.width(9.dp))
                Text(
                    if (saving) "Saving..." else if (existing == null) "Add task to board" else "Save changes",
                    color = tokens.neutrals.bg.toColor(),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.let {
                LocalDate.parse(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        whenPath = WhenPath.DATE
                    }
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = time?.first ?: 9,
            initialMinute = time?.second ?: 0,
            is24Hour = settings.use24HourTime,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { time = tpState.hour to tpState.minute; showTimePicker = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = tpState) },
            containerColor = tokens.neutrals.surfaceRaised.toColor(),
        )
    }

    if (showRepeatPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRepeatPicker = false },
            confirmButton = {},
            title = { Text("Repeat", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    REPEAT_CHOICES.forEach { choice ->
                        val selected = repeat == choice
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { repeat = choice; showRepeatPicker = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MaterialSymbol(
                                "check_circle",
                                if (selected) todayAccent else tokens.neutrals.textTertiary.toColor(),
                                size = 20.sp,
                                filled = selected,
                            )
                            Text(
                                choice.label,
                                color = tokens.neutrals.textPrimary.toColor(),
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            containerColor = tokens.neutrals.surfaceRaised.toColor(),
        )
    }

    if (showConsent) {
        ConsentDialog(
            message = viewModel.consentPrompt(),
            onConfirm = {
                scope.launch {
                    val ok = viewModel.grantRecurrence()
                    showConsent = false
                    if (ok) submit() else { repeat = REPEAT_CHOICES.first(); error = "Recurring tasks are unavailable for this database." }
                }
            },
            onDismiss = { showConsent = false; repeat = REPEAT_CHOICES.first() },
        )
    }
}

private fun hasDate(whenPath: WhenPath?, selectedDate: String?): Boolean =
    whenPath == WhenPath.DATE && selectedDate != null

private fun humanDate(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate)
        val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "$month ${date.dayOfMonth}"
    } catch (_: Exception) { isoDate }
}

private data class RepeatChoice2(val label: String, val chipLabel: String, val rrule: String?)
private val REPEAT_CHOICES = listOf(
    RepeatChoice2("Does not repeat", "None", null),
    RepeatChoice2("Every day", "Daily", "FREQ=DAILY"),
    RepeatChoice2("Every week", "Weekly", "FREQ=WEEKLY"),
    RepeatChoice2("Every weekday", "Weekdays", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
    RepeatChoice2("Every month", "Monthly", "FREQ=MONTHLY"),
)

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        color = SiftTheme.tokens.neutrals.textTertiary.toColor(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.04.sp,
    )
}

@Composable
private fun ToggleSegment(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val bg = if (selected) tokens.neutrals.textPrimary.toColor() else Color.Transparent
    val fg = if (selected) tokens.neutrals.bg.toColor() else tokens.neutrals.textTertiary.toColor()
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(icon, fg, size = 16.sp, filled = selected)
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickDateChip(label: String, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    Text(
        label,
        color = tokens.neutrals.textSecondary.toColor(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MaterialSymbol(bucket.icon, fg, size = 16.sp, filled = true)
        Text(bucket.displayName, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

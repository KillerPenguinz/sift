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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.domain.SiftLabel
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainDumpDetailSheet(
    item: ProjectedItem,
    viewModel: BoardViewModel,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val labels by viewModel.siftLabels.collectAsStateWithLifecycle()
    val labelMap by viewModel.localStateLabelMap.collectAsStateWithLifecycle()
    val localStates by viewModel.localStatesMap.collectAsStateWithLifecycle()
    val currentLabelId = labelMap[item.task.pageId]
    val localState = localStates[item.task.pageId]

    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(item.task.pageId) { mutableStateOf(item.task.title) }
    var editNotes by remember(item.task.pageId) { mutableStateOf(item.task.notes.orEmpty()) }
    var showPromoteChoice by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
        ) {
            // Title
            if (editing) {
                BasicTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    textStyle = TextStyle(
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = (24 * 1.12).sp,
                        letterSpacing = (-0.02).sp,
                        color = tokens.neutrals.textPrimary.toColor(),
                    ),
                    cursorBrush = SolidColor(tokens.neutrals.textPrimary.toColor()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            } else {
                Text(
                    editTitle,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = (24 * 1.12).sp,
                    letterSpacing = (-0.02).sp,
                    color = tokens.neutrals.textPrimary.toColor(),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Notes
            if (editing) {
                BasicTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    textStyle = TextStyle(
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        color = tokens.neutrals.textSecondary.toColor(),
                    ),
                    cursorBrush = SolidColor(tokens.neutrals.textPrimary.toColor()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.neutrals.surface.toColor())
                        .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .height(80.dp),
                    decorationBox = { inner ->
                        if (editNotes.isEmpty()) {
                            Text(
                                "Add notes...",
                                color = tokens.neutrals.textTertiary.toColor(),
                                fontSize = 14.5.sp,
                            )
                        }
                        inner()
                    },
                )
            } else if (editNotes.isNotEmpty()) {
                Text(
                    editNotes,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = tokens.neutrals.textSecondary.toColor(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Label selector
            if (labels.isNotEmpty()) {
                Text(
                    "Label",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = tokens.neutrals.textSecondary.toColor(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEach { label ->
                        val selected = currentLabelId == label.id
                        LabelChip(
                            label = label,
                            selected = selected,
                            onClick = {
                                viewModel.assignLabel(
                                    item.task.pageId,
                                    if (selected) null else label.id,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Creation and modification dates
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                item.task.reviewDateIso?.let { date ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        MaterialSymbol("schedule", tokens.neutrals.textTertiary.toColor(), size = 16.sp)
                        Text(
                            "Added ${humanRelativeDate(date)}",
                            color = tokens.neutrals.textTertiary.toColor(),
                            fontSize = 13.sp,
                        )
                    }
                }
                localState?.lastModifiedAt?.let { millis ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MaterialSymbol("edit_note", tokens.neutrals.textTertiary.toColor(), size = 16.sp)
                        Text(
                            "Modified ${humanRelativeMillis(millis)}",
                            color = tokens.neutrals.textTertiary.toColor(),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Actions row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Edit / Save toggle
                ActionPill(
                    icon = if (editing) "check" else "edit",
                    label = if (editing) "Save" else "Edit",
                    onClick = {
                        if (editing) {
                            // Save edits
                            scope.launch {
                                viewModel.editTask(
                                    item.task.pageId,
                                    com.ironclinicgym.sift.core.domain.TaskEdits(
                                        title = if (editTitle != item.task.title) com.ironclinicgym.sift.core.domain.TaskEdits.Field(editTitle) else null,
                                        notes = if (editNotes != (item.task.notes ?: "")) com.ironclinicgym.sift.core.domain.TaskEdits.Field(editNotes) else null,
                                    ),
                                )
                            }
                            editing = false
                        } else {
                            editing = true
                        }
                    },
                )

                // Promote to board
                if (!showPromoteChoice) {
                    ActionPill(
                        icon = "check_circle",
                        label = "To board",
                        onClick = { showPromoteChoice = true },
                    )
                }

                // Delete
                ActionPill(
                    icon = "delete",
                    label = "Delete",
                    onClick = {
                        viewModel.removeTask(item.task.pageId)
                        onDismiss()
                    },
                )
            }

            // Promote choice: ASAP or pick a date
            if (showPromoteChoice) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionPill(
                        icon = "bolt",
                        label = "ASAP",
                        onClick = {
                            viewModel.promoteBrainDump(item.task.pageId)
                            onDismiss()
                        },
                    )
                    ActionPill(
                        icon = "calendar_today",
                        label = "Pick a date",
                        onClick = { showDatePicker = true },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val dateIso = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                            viewModel.promoteBrainDumpWithDate(item.task.pageId, dateIso)
                            showDatePicker = false
                            onDismiss()
                        }
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun LabelChip(
    label: SiftLabel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val chipColor = try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(label.colorHex))
    } catch (_: Exception) {
        tokens.neutrals.textTertiary.toColor()
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background(chipColor.copy(alpha = 0.15f))
                else Modifier.background(tokens.neutrals.surface.toColor())
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) chipColor else tokens.neutrals.border.toColor(),
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MaterialSymbol(label.icon, chipColor, size = 15.sp)
        Text(
            label.name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) chipColor else tokens.neutrals.textSecondary.toColor(),
        )
    }
}

@Composable
private fun ActionPill(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MaterialSymbol(icon, tokens.neutrals.textSecondary.toColor(), size = 17.sp)
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.neutrals.textSecondary.toColor(),
        )
    }
}

private fun humanRelativeDate(isoDate: String): String {
    return try {
        val date = java.time.LocalDate.parse(isoDate.take(10))
        val today = java.time.LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 14L -> "last week"
            else -> "${days / 7} weeks ago"
        }
    } catch (_: Exception) { isoDate }
}

private fun humanRelativeMillis(millis: Long): String {
    val elapsed = System.currentTimeMillis() - millis
    return when {
        elapsed < 60_000 -> "just now"
        elapsed < 3_600_000 -> "${elapsed / 60_000} min ago"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000}h ago"
        elapsed < 172_800_000 -> "yesterday"
        elapsed < 604_800_000 -> "${elapsed / 86_400_000} days ago"
        else -> "${elapsed / 604_800_000} weeks ago"
    }
}

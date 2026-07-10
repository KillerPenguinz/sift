package com.ironclinicgym.sift.ui.board

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.PriorityView
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.board.priorityLabelForTask
import com.ironclinicgym.sift.core.notion.NotionUrls
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailSheet(
    item: ProjectedItem,
    priorityName: String,
    settings: BoardSettings?,
    onLoadBody: suspend () -> List<String>,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onPin: () -> Unit,
    onRemove: () -> Unit,
    onChangeDate: () -> Unit,
    onChangePriority: (PriorityView) -> Unit,
    onDismiss: () -> Unit,
    /**
     * True while the host is showing the protected friction dialog. A chip tap that diverts to
     * friction never applied the move, so the sheet reverts its optimistic chip selection.
     */
    frictionActive: Boolean = false,
) {
    val tokens = SiftTheme.tokens
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bucketColors = bucketColorsOf(item.bucket.colorKey)
    val todayIso = remember { java.time.LocalDate.now().toString() }
    var expanded by remember { mutableStateOf(false) }
    // Local pin level so the icon updates immediately: onPin no longer dismisses the sheet, and
    // the task object passed in won't recompose with the new pin state until the parent's data
    // refreshes, so the sheet tracks its own optimistic copy of the cycle.
    var localPinLevel by remember(item.task.pageId) { mutableIntStateOf(item.task.pinLevel) }
    var pinStateLabel by remember { mutableStateOf<String?>(null) }
    var showInlinePriority by remember(item.task.pageId) { mutableStateOf(false) }
    // Local override for which priority chip reads as "selected" in the inline picker, for the
    // same reason as localPinLevel: item.task won't recompose in place after a move.
    var localPriorityOptionId by remember(item.task.pageId) { mutableStateOf(item.task.priorityOptionId) }
    // A chip tap that lands on protected friction never applied the move, so revert the
    // optimistic selection as soon as the friction dialog appears. If the user then confirms
    // the dialog, the chip shows the pre-move value until reopen, the same staleness the frozen
    // task snapshot already has everywhere, and not misleading in a harmful direction.
    LaunchedEffect(frictionActive) {
        if (frictionActive) localPriorityOptionId = item.task.priorityOptionId
    }
    LaunchedEffect(pinStateLabel) {
        if (pinStateLabel != null) {
            delay(2000)
            pinStateLabel = null
        }
    }
    val body by produceState<List<String>?>(initialValue = null, item.task.pageId) {
        value = runCatching { onLoadBody() }.getOrDefault(emptyList())
    }

    val priorityView = settings?.priorities?.firstOrNull { it.optionId == item.task.priorityOptionId }
    val priorityColors = priorityView?.let { priorityColorsOf(it.colorKey) }
    val derivedPriority = priorityView?.let { pv -> tokens.priorities.firstOrNull { it.key.name == pv.colorKey } }
    val greenAccent = tokens.successAccent.toColor()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 26.dp),
        ) {
            // Title + pin button
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    item.task.title,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    lineHeight = (27 * 1.12).sp,
                    letterSpacing = (-0.02).sp,
                    color = tokens.neutrals.textPrimary.toColor(),
                    modifier = Modifier.weight(1f),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.neutrals.surface.toColor())
                            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
                            .clickable {
                                // Mirror BoardViewModel.pinTask's cycle so the icon updates in place
                                // even though the sheet stays open and the underlying task snapshot
                                // does not recompose.
                                localPinLevel = when {
                                    localPinLevel == 0 -> 1
                                    localPinLevel == 1 && item.task.isRecurring -> 2
                                    else -> 0
                                }
                                pinStateLabel = when (localPinLevel) {
                                    1 -> "Pinned"
                                    2 -> "Protected pin"
                                    else -> "Unpinned"
                                }
                                onPin()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (localPinLevel) {
                            0 -> MaterialSymbol(
                                "push_pin",
                                tokens.neutrals.textTertiary.toColor(),
                                size = 21.sp,
                                filled = false,
                            )
                            1 -> MaterialSymbol(
                                "push_pin",
                                tokens.neutrals.textPrimary.toColor(),
                                size = 21.sp,
                                filled = true,
                            )
                            2 -> {
                                Box(contentAlignment = Alignment.Center) {
                                    MaterialSymbol(
                                        "push_pin",
                                        tokens.neutrals.textPrimary.toColor(),
                                        size = 21.sp,
                                        filled = true,
                                    )
                                    MaterialSymbol(
                                        "shield",
                                        tokens.successAccent.toColor(),
                                        size = 10.sp,
                                        filled = true,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        when (localPinLevel) {
                            0 -> "Not pinned"
                            1 -> "Pinned (this instance)"
                            2 -> "Protected pin (all occurrences)"
                            else -> ""
                        },
                        color = tokens.neutrals.textTertiary.toColor(),
                        fontSize = 11.sp,
                    )
                }
                AnimatedVisibility(visible = pinStateLabel != null) {
                    Text(
                        pinStateLabel ?: "",
                        color = tokens.neutrals.textTertiary.toColor(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Bucket + priority chips
            Row(
                Modifier.fillMaxWidth().padding(bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.bucket.optionId != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(bucketColors.bg.toColor())
                            .border(1.dp, bucketColors.color.toColor().copy(alpha = 0.30f), RoundedCornerShape(11.dp))
                            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MaterialSymbol(item.bucket.icon, bucketColors.onColor.toColor(), size = 17.sp, filled = true)
                        Text(
                            item.bucket.displayName,
                            color = bucketColors.onColor.toColor(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (priorityColors != null) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(priorityColors.headerBg.toColor())
                            .border(1.dp, priorityColors.accent.toColor().copy(alpha = 0.30f), RoundedCornerShape(11.dp))
                            .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MaterialSymbol(
                            derivedPriority?.icon ?: "label",
                            priorityColors.accentText.toColor(),
                            size = 16.sp,
                            filled = true,
                        )
                        Text(
                            priorityLabelForTask(
                                isDated = item.task.isDated,
                                dueIso = item.task.due,
                                todayIso = todayIso,
                                priorityDisplayName = priorityName,
                            ),
                            color = priorityColors.accentText.toColor(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Prominent Complete button
            if (!item.task.isDone) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(greenAccent)
                        .clickable(onClick = onComplete)
                        .padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol("check_circle", tokens.neutrals.bg.toColor(), size = 23.sp, filled = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Complete",
                        color = tokens.neutrals.bg.toColor(),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Edit + Remove secondary row (no Move per standing rules)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SecondaryActionButton("edit", "Edit", onEdit, Modifier.weight(1f))
                SecondaryActionButton("delete", "Remove", onRemove, Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            if (item.task.isDated) {
                // Dated tasks keep the date redirect behavior; they cannot be dragged or picked
                // between priorities directly (Phase 3.5 rule).
                SecondaryActionButton("calendar_today", "Change date", onChangeDate, Modifier.fillMaxWidth())
            } else {
                SecondaryActionButton(
                    "swap_vert",
                    "Change priority",
                    { showInlinePriority = !showInlinePriority },
                    Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = showInlinePriority) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings?.priorities
                            ?.filter { !it.hidden }
                            ?.sortedBy { it.order }
                            ?.forEach { pv ->
                                val chipColors = priorityColorsOf(pv.colorKey)
                                val selected = pv.optionId == localPriorityOptionId
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipColors.headerBg.toColor())
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            chipColors.accent.toColor(),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            localPriorityOptionId = pv.optionId
                                            onChangePriority(pv)
                                            showInlinePriority = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    MaterialSymbol(
                                        priorityIconFor(pv.colorKey),
                                        chipColors.accentText.toColor(),
                                        size = 16.sp,
                                        filled = true,
                                    )
                                    Text(
                                        pv.displayName,
                                        color = chipColors.accentText.toColor(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                    }
                }
            }

            // Expandable hint
            val hasNotes = item.task.notes?.isNotBlank() == true
            val hasLabels = item.labels.isNotEmpty()
            val hasRecurrence = item.task.recurrenceDisplay?.isNotBlank() == true
            val hasExpandable = hasNotes || hasLabels || hasRecurrence || body != null

            if (hasExpandable && !expanded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -10f) expanded = true
                            }
                        }
                        .clickable { expanded = true }
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialSymbol("expand_less", tokens.neutrals.textTertiary.toColor(), size = 16.sp, filled = false)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Swipe up for notes & details",
                        color = tokens.neutrals.textTertiary.toColor(),
                        fontSize = 13.sp,
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 20.dp)) {
                    // Notes
                    if (hasNotes) {
                        DetailSectionLabel("Notes")
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tokens.neutrals.surface.toColor())
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(14.dp))
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                        ) {
                            Text(
                                item.task.notes!!,
                                color = tokens.neutrals.textSecondary.toColor(),
                                fontSize = 14.5.sp,
                                lineHeight = (14.5 * 1.5).sp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // Labels
                    if (hasLabels) {
                        DetailSectionLabel("Labels")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            item.labels.forEach { label ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(tokens.neutrals.surface.toColor())
                                        .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        Modifier.size(7.dp).clip(CircleShape)
                                            .background(tokens.neutrals.textTertiary.toColor()),
                                    )
                                    Text(
                                        label,
                                        color = tokens.neutrals.textSecondary.toColor(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // Recurrence
                    if (hasRecurrence) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tokens.neutrals.surface.toColor())
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MaterialSymbol("repeat", tokens.neutrals.textSecondary.toColor(), size = 20.sp, filled = false)
                            Text(
                                item.task.recurrenceDisplay!!,
                                color = tokens.neutrals.textSecondary.toColor(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    // Provenance footer
                    val borderColor = tokens.neutrals.border.toColor()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(borderColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                            }
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Via", color = tokens.neutrals.textTertiary.toColor(), fontSize = 12.5.sp)

                        val notionUrl = NotionUrls.pageUrl(item.task.pageId)
                        if (item.task.createdBy == "notion" || (item.task.pageId.isNotBlank() && notionUrl.contains("notion"))) {
                            Text("Notion", color = tokens.neutrals.textSecondary.toColor(), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tokens.neutrals.surface.toColor())
                                    .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(8.dp))
                                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notionUrl))) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Notion", color = tokens.neutrals.textTertiary.toColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                MaterialSymbol("open_in_new", tokens.neutrals.textTertiary.toColor(), size = 14.sp, filled = false)
                            }
                        } else {
                            Text("Sift", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tokens.neutrals.textSecondary.toColor())
                            Text(".", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tokens.priorities.first().accent.toColor())
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SecondaryActionButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = SiftTheme.tokens
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(icon, tokens.neutrals.textSecondary.toColor(), size = 19.sp, filled = false)
        Spacer(Modifier.width(7.dp))
        Text(label, color = tokens.neutrals.textSecondary.toColor(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = SiftTheme.tokens.neutrals.textTertiary.toColor(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.05.sp,
    )
}

package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.domain.SiftLabel
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun BrainDumpScreen(
    viewModel: BoardViewModel,
    onTap: (ProjectedItem) -> Unit,
    onOpenMenu: () -> Unit = {},
) {
    val tokens = SiftTheme.tokens
    val items by viewModel.brainDumpItems.collectAsStateWithLifecycle()
    val labels by viewModel.siftLabels.collectAsStateWithLifecycle()
    val labelMap by viewModel.localStateLabelMap.collectAsStateWithLifecycle()
    val localStates by viewModel.localStatesMap.collectAsStateWithLifecycle()
    val sortMode by viewModel.brainDumpSort.collectAsStateWithLifecycle()
    val sortAscending by viewModel.brainDumpSortAscending.collectAsStateWithLifecycle()
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    var showLabelManager by remember { mutableStateOf(false) }

    // Apply sorting
    val sortedItems = remember(items, sortMode, sortAscending, labelMap, labels, localStates) {
        val labelsById = labels.associateBy { it.id }
        val sorted = when (sortMode) {
            BoardViewModel.BrainDumpSort.LABEL -> items.sortedBy { item ->
                val lid = labelMap[item.task.pageId]
                val label = lid?.let { labelsById[it] }
                label?.name ?: "￿" // unlabeled sorts last
            }
            BoardViewModel.BrainDumpSort.CREATED -> items.sortedBy { it.task.reviewDateIso ?: "" }
            BoardViewModel.BrainDumpSort.MODIFIED -> items.sortedByDescending {
                localStates[it.task.pageId]?.lastModifiedAt ?: 0L
            }
        }
        if (!sortAscending) sorted.reversed() else sorted
    }

    Column(Modifier.fillMaxSize().padding(top = topInset)) {
        // Header (matches board)
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiftWordmark()
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clickable { viewModel.refresh() }, contentAlignment = Alignment.Center) {
                    MaterialSymbol("refresh", tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = false)
                }
                val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
                MenuIconWithBadge(showBadge = unreadNotifications > 0, onClick = onOpenMenu)
            }
        }

        // Sub-header slot: the "Brain dump" row when idle, or the current notification when one
        // is queued. The undo bar (if any) takes priority over the queue.
        SubheaderSlot(viewModel = viewModel, bottomPadding = 16.dp) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialSymbol("lightbulb", tokens.neutrals.textSecondary.toColor(), size = 20.sp, filled = true)
                    Text(
                        "Brain dump",
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.01).sp,
                        color = tokens.neutrals.textPrimary.toColor(),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Sort control
                    if (items.isNotEmpty()) {
                        val sortLabel = when (sortMode) {
                            BoardViewModel.BrainDumpSort.LABEL -> "By label"
                            BoardViewModel.BrainDumpSort.CREATED -> "By date"
                            BoardViewModel.BrainDumpSort.MODIFIED -> "By modified"
                        }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.neutrals.surface.toColor())
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
                                .clickable { viewModel.cycleBrainDumpSort() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MaterialSymbol("sort", tokens.neutrals.textTertiary.toColor(), size = 14.sp)
                            Text(
                                sortLabel,
                                color = tokens.neutrals.textTertiary.toColor(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        // Direction toggle
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.toggleBrainDumpSortDirection() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MaterialSymbol(
                                if (sortAscending) "arrow_upward" else "arrow_downward",
                                tokens.neutrals.textTertiary.toColor(),
                                size = 16.sp,
                            )
                        }
                    }
                    // Count badge
                    if (items.isNotEmpty()) {
                        val countText = if (items.size == 1) "1 thought" else "${items.size} thoughts"
                        Text(
                            countText,
                            color = tokens.neutrals.textTertiary.toColor(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.neutrals.surface.toColor())
                                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    // Label manager button
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLabelManager = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        MaterialSymbol("sell", tokens.neutrals.textTertiary.toColor(), size = 18.sp)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet. Use \"Brain dump\" when adding a task.",
                    color = tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sortMode == BoardViewModel.BrainDumpSort.LABEL) {
                    // Group items by label with section headers
                    val labelsById = labels.associateBy { it.id }
                    val grouped = sortedItems.groupBy { item ->
                        val lid = labelMap[item.task.pageId]
                        lid?.let { labelsById[it] }
                    }
                    val labeledGroups = grouped.filterKeys { it != null }
                        .entries.sortedBy { it.key!!.name }
                        .let { if (!sortAscending) it.reversed() else it }
                    val unlabeled = grouped[null] ?: emptyList()

                    labeledGroups.forEach { (label, groupItems) ->
                        item(key = "header_${label!!.id}") {
                            LabelSectionHeader(label = label)
                        }
                        items(groupItems, key = { it.task.pageId }) { item ->
                            BrainDumpCard(item = item, label = label, onClick = { onTap(item) })
                        }
                    }
                    if (unlabeled.isNotEmpty()) {
                        item(key = "header_unlabeled") {
                            UnlabeledSectionHeader()
                        }
                        items(unlabeled, key = { it.task.pageId }) { item ->
                            BrainDumpCard(item = item, label = null, onClick = { onTap(item) })
                        }
                    }
                } else {
                    items(sortedItems, key = { it.task.pageId }) { item ->
                        val lid = labelMap[item.task.pageId]
                        val label = lid?.let { id -> labels.firstOrNull { it.id == id } }
                        BrainDumpCard(item = item, label = label, onClick = { onTap(item) })
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showLabelManager) {
        LabelManagerSheet(
            viewModel = viewModel,
            onDismiss = { showLabelManager = false },
        )
    }
}

@Composable
private fun BrainDumpCard(item: ProjectedItem, label: SiftLabel?, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val accentColor = label?.let {
        try {
            Color(android.graphics.Color.parseColor(it.colorHex))
        } catch (_: Exception) { null }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(15.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        // Left accent bar for label color
        if (accentColor != null) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
        }

        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Label icon or nothing
            if (label != null && accentColor != null) {
                Box(
                    Modifier.padding(top = 2.dp),
                ) {
                    MaterialSymbol(label.icon, accentColor, size = 18.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.task.title,
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (15.5 * 1.35).sp,
                )
                item.task.reviewDateIso?.let { date ->
                    val relative = humanRelativeDate(date)
                    Text(
                        relative,
                        color = tokens.neutrals.textTertiary.toColor(),
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            MaterialSymbol(
                "north_east",
                tokens.neutrals.textTertiary.toColor(),
                size = 19.sp,
                filled = false,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LabelSectionHeader(label: SiftLabel) {
    val tokens = SiftTheme.tokens
    val accentColor = try {
        Color(android.graphics.Color.parseColor(label.colorHex))
    } catch (_: Exception) {
        tokens.neutrals.textSecondary.toColor()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MaterialSymbol(label.icon, accentColor, size = 17.sp)
        Text(
            label.name,
            fontFamily = Bricolage,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = accentColor,
        )
    }
}

@Composable
private fun UnlabeledSectionHeader() {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MaterialSymbol("label_off", tokens.neutrals.textTertiary.toColor(), size = 17.sp)
        Text(
            "Unlabeled",
            fontFamily = Bricolage,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = tokens.neutrals.textTertiary.toColor(),
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

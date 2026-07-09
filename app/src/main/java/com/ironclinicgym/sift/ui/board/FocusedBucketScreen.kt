package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.Landmark
import com.ironclinicgym.sift.core.board.OneDayLandmarks
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * Full screen view of one priority: an accent header band, bucket filter chips, the fuller item
 * rows, and a quiet completed group at the bottom. Read only in Phase 2 (no checking off yet).
 */
@Composable
fun FocusedPriorityScreen(viewModel: BoardViewModel, priorityId: String, onBack: () -> Unit) {
    val priority by viewModel.focusedPriority(priorityId).collectAsStateWithLifecycle(initialValue = null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val undoToken by viewModel.undoToken.collectAsStateWithLifecycle()
    val flashPageId by viewModel.flashPageId.collectAsStateWithLifecycle()
    val redirectPrompt by viewModel.redirectPrompt.collectAsStateWithLifecycle()
    val protectedFriction by viewModel.protectedFriction.collectAsStateWithLifecycle()
    var bucketFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ProjectedItem?>(null) }
    var editing by remember { mutableStateOf<ProjectedItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var itemForPriorityPicker by remember { mutableStateOf<ProjectedItem?>(null) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    val activeNotification by viewModel.activeNotification.collectAsStateWithLifecycle()
    val tokens = SiftTheme.tokens
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    Surface(Modifier.fillMaxSize(), color = tokens.neutrals.bg.toColor()) {
      Box(Modifier.fillMaxSize()) {
        val current = priority
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }

        val colors = priorityColorsOf(current.view.colorKey)
        val visibleItems = current.items.filter { bucketFilter == null || it.bucket.optionId == bucketFilter }
        val buckets = (current.items + current.completed).map { it.bucket }.distinctBy { it.optionId }

        Column(Modifier.fillMaxSize()) {
            // Header band
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.headerBg.toColor())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = topInset + 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        MaterialSymbol("arrow_back", color = colors.accentText.toColor(), size = 22.sp)
                    }
                    MaterialSymbol(priorityIconFor(current.view.colorKey), color = colors.accentText.toColor(), size = 22.sp)
                    Text(
                        current.view.displayName.lowercase(),
                        color = colors.accentText.toColor(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${current.totalCount}", color = colors.accentText.toColor(), fontWeight = FontWeight.SemiBold)
                }
            }

            // Bucket filter chips
            if (buckets.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("All", selected = bucketFilter == null) { bucketFilter = null }
                    buckets.forEach { s ->
                        FilterChip(s.displayName, selected = bucketFilter == s.optionId) { bucketFilter = s.optionId }
                    }
                }
            }

            val isOneDayPriority = current.view.colorKey == "ONEDAY"
            val enabledLandmarks = settings?.oneDayLandmarksEnabled ?: emptySet()

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 16.dp),
            ) {
                if (isOneDayPriority && current.landmarks.isNotEmpty()) {
                    // Build ordered landmark groups filtered by bucket selection and enabled toggles.
                    val landmarkGroups = Landmark.entries
                        .filter { it.name in enabledLandmarks }
                        .mapNotNull { landmark ->
                            val filtered = (current.landmarks[landmark] ?: emptyList())
                                .filter { bucketFilter == null || it.bucket.optionId == bucketFilter }
                            if (filtered.isNotEmpty()) landmark to filtered else null
                        }
                    if (landmarkGroups.isEmpty()) {
                        item { Text("Nothing here", color = tokens.neutrals.textTertiary.toColor(), modifier = Modifier.padding(vertical = 12.dp)) }
                    } else {
                        landmarkGroups.forEach { (landmark, landmarkItems) ->
                            item(key = "landmark_${landmark.name}") {
                                Text(
                                    text = OneDayLandmarks.displayLabel(landmark),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.neutrals.textTertiary.toColor(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 20.dp, bottom = 4.dp),
                                )
                            }
                            items(landmarkItems, key = { it.task.pageId }) { item ->
                                SwipeableFocusedRow(
                                    item = item,
                                    flashing = item.task.pageId == flashPageId,
                                    onComplete = { viewModel.completeTask(item.task.pageId) },
                                    onSnooze = { viewModel.snoozeTask(item.task) },
                                    onClick = { selected = item },
                                )
                            }
                        }
                    }
                } else {
                    if (visibleItems.isEmpty()) {
                        item { Text("Nothing here", color = tokens.neutrals.textTertiary.toColor(), modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                    items(visibleItems, key = { it.task.pageId }) { item ->
                        SwipeableFocusedRow(
                            item = item,
                            flashing = item.task.pageId == flashPageId,
                            onComplete = { viewModel.completeTask(item.task.pageId) },
                            onSnooze = { viewModel.snoozeTask(item.task) },
                            onClick = { selected = item },
                        )
                    }
                }

                val completed = current.completed.filter { bucketFilter == null || it.bucket.optionId == bucketFilter }
                if (completed.isNotEmpty()) {
                    item {
                        Text(
                            "Completed today",
                            color = tokens.neutrals.textTertiary.toColor(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(completed, key = { it.task.pageId }) { item -> FocusedRow(item, done = true) { selected = item } }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { showAdd = true }.padding(vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MaterialSymbol("add", tokens.neutrals.textSecondary.toColor(), size = 22.sp)
                        Text("Add a task", color = tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        val currentUndo = undoToken
        val currentNotification = activeNotification

        if (currentUndo != null) {
            TopNotificationBar(
                variant = NotificationVariant.Reversible(
                    message = currentUndo.label,
                    icon = "swap_horiz",
                    onUndo = { viewModel.undo() },
                ),
                onDismiss = { viewModel.dismissUndo() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        } else if (currentNotification != null) {
            TopNotificationBar(
                variant = currentNotification,
                onDismiss = { viewModel.dismissNotification() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        }

        selected?.let { item ->
            TaskDetailSheet(
                item = item,
                priorityName = current.view.displayName,
                settings = settings,
                onLoadBody = { viewModel.loadPageBody(item.task.pageId) },
                onEdit = { editing = item; selected = null },
                onComplete = { viewModel.completeTask(item.task.pageId); selected = null },
                onPin = { viewModel.pinTask(item.task.pageId); selected = null },
                onRemove = { viewModel.removeTask(item.task.pageId); selected = null },
                onChangeDate = {
                    val task = selected?.task ?: return@TaskDetailSheet
                    selected = null
                    viewModel.openRedirectPromptForTask(task)
                },
                onChangePriority = {
                    itemForPriorityPicker = selected
                    selected = null
                    showPriorityPicker = true
                },
                onDismiss = { selected = null },
            )
        }

        if (showPriorityPicker) {
            val pickerItem = itemForPriorityPicker
            if (pickerItem == null) {
                showPriorityPicker = false
            } else {
                PriorityPickerSheet(
                    priorities = settings?.priorities ?: emptyList(),
                    currentPriorityOptionId = pickerItem.task.priorityOptionId,
                    onSelect = { pv ->
                        viewModel.moveTask(pickerItem.task.pageId, pv.optionName ?: pv.displayName, pv.optionId)
                    },
                    onDismiss = { showPriorityPicker = false; itemForPriorityPicker = null },
                )
            }
        }

        val activeSettings = settings
        if ((showAdd || editing != null) && activeSettings != null) {
            AddTaskSheetV2(
                viewModel = viewModel,
                settings = activeSettings,
                editing = editing,
                onDismiss = { showAdd = false; editing = null },
            )
        }

        redirectPrompt?.let { prompt ->
            RedirectPromptSheet(
                prompt = prompt,
                onChangeDate = { viewModel.dismissRedirectPrompt() },
                onSnooze = { viewModel.snoozeTask(prompt.task); viewModel.dismissRedirectPrompt() },
                onPin = { viewModel.pinTask(prompt.task.pageId); viewModel.dismissRedirectPrompt() },
                onRemoveDate = { viewModel.removeDateAndMoveManual(prompt.task.pageId); viewModel.dismissRedirectPrompt() },
                onDismiss = { viewModel.dismissRedirectPrompt() },
            )
        }

        protectedFriction?.let { friction ->
            ProtectedFrictionDialog(
                friction = friction,
                onConfirm = {
                    viewModel.confirmProtectedAction(friction.task.pageId, friction.targetName ?: "", friction.targetId)
                },
                onDismiss = { viewModel.dismissProtectedFriction() },
            )
        }
      }
    }
}

/**
 * A focused-list row with first-class swipes: swipe right to complete, swipe left to snooze.
 * Remove stays behind the safer detail-sheet action (with undo). confirmValueChange fires the
 * action and returns false so the row settles back; the data flow removes it if it left the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableFocusedRow(
    item: ProjectedItem,
    flashing: Boolean,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> onComplete()
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> onSnooze()
                androidx.compose.material3.SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val toEnd = dismissState.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
            val icon = if (toEnd) "check_circle" else "schedule"
            val label = if (toEnd) "Complete" else "Snooze"
            val bgColor = if (toEnd) tokens.priorities[3].accent.toColor().copy(alpha = 0.85f)
                else tokens.priorities[2].accent.toColor().copy(alpha = 0.85f)
            val align = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = align,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (toEnd) {
                        MaterialSymbol(icon, tokens.neutrals.bg.toColor(), size = 20.sp)
                        Text(label, color = tokens.neutrals.bg.toColor(), style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text(label, color = tokens.neutrals.bg.toColor(), style = MaterialTheme.typography.labelMedium)
                        MaterialSymbol(icon, tokens.neutrals.bg.toColor(), size = 20.sp)
                    }
                }
            }
        },
    ) {
        Box(Modifier.background(tokens.neutrals.bg.toColor())) {
            FocusedRow(item, done = false, flashing = flashing, onClick = onClick)
        }
    }
}

@Composable
private fun FocusedRow(item: ProjectedItem, done: Boolean, flashing: Boolean = false, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val sColors = bucketColorsOf(item.bucket.colorKey)
    val flash by flashBackground(flashing, sColors.bg.toColor())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(flash)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BucketTile(item.bucket.icon, sColors, tile = 34)
        Column(Modifier.weight(1f)) {
            Text(
                item.task.title,
                color = if (done) tokens.neutrals.textTertiary.toColor() else tokens.neutrals.textPrimary.toColor(),
                style = MaterialTheme.typography.bodyLarge,
            )
            val bucketLabel = item.bucket.optionId?.let { item.bucket.displayName }
            val meta = listOfNotNull(bucketLabel, item.timeLabel.takeIf { it.isNotEmpty() }).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, color = tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.bodySmall)
        }
        if (item.isOverdue && !done) {
            Text("overdue", color = tokens.neutrals.textTertiary.toColor(), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val bg = if (selected) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.surface.toColor()
    val fg = if (selected) tokens.neutrals.bg.toColor() else tokens.neutrals.textSecondary.toColor()
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

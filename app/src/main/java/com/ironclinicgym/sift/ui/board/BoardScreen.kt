package com.ironclinicgym.sift.ui.board

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.domain.SupportUrls
import com.ironclinicgym.sift.core.theme.PRIORITY_META
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/** Priority icon by its color key (urgency accent), from the theme meta; neutral fallback. */
fun priorityIconFor(colorKey: String): String =
    PRIORITY_META.firstOrNull { it.key.name == colorKey }?.icon ?: "label"

private const val ROWS_TWO_COLUMN = 4
private const val ROWS_ONE_COLUMN = 8

/**
 * The priority grid. Cached priorities render immediately in a masonry (2 column) or fuller
 * (1 column) layout that adapts to whatever priorities exist. Minimize, layout, and refresh are
 * one-tap controls; a failed refresh keeps the board and shows a quiet status line.
 */
@Composable
fun BoardScreen(
    viewModel: BoardViewModel,
    fromSetup: Boolean = false,
    onOpenPriority: (String) -> Unit,
    onSettings: () -> Unit,
    onEditTask: (com.ironclinicgym.sift.core.board.ProjectedItem) -> Unit = {},
) {
    val projection by viewModel.projection.collectAsStateWithLifecycle()
    val label by viewModel.boardLabel.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val refresh by viewModel.refresh.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdatedLabel.collectAsStateWithLifecycle()
    val undoToken by viewModel.undoToken.collectAsStateWithLifecycle()
    val showReminder by viewModel.showNotionReminder.collectAsStateWithLifecycle()
    val redirectPrompt by viewModel.redirectPrompt.collectAsStateWithLifecycle()
    val protectedFriction by viewModel.protectedFriction.collectAsStateWithLifecycle()
    val tokens = SiftTheme.tokens
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var selectedTask by remember { mutableStateOf<Pair<com.ironclinicgym.sift.core.board.ProjectedItem, String>?>(null) }
    var taskForPriorityPicker by remember { mutableStateOf<com.ironclinicgym.sift.core.board.ProjectedItem?>(null) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Refresh when the board opens, so data is current without waiting on the background cycle.
    LaunchedEffect(Unit) { viewModel.refresh() }
    // Surface transient write messages (errors, snooze notices).
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // One-shot toast when arriving from setup.
    LaunchedEffect(fromSetup) {
        if (fromSetup) snackbar.showSnackbar("Setup complete. Syncing with Notion.")
    }

    Surface(Modifier.fillMaxSize(), color = tokens.neutrals.bg.toColor()) {
      Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding()),
        ) {
            // Header controls
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
                    val minimized = settings?.minimized == true
                    val twoColumn = settings?.twoColumn != false
                    ControlIcon(if (minimized) "unfold_more" else "unfold_less", filled = false) { viewModel.toggleMinimized() }
                    ControlIcon("grid_view", filled = !twoColumn) { viewModel.toggleLayout() }
                    RefreshIcon(
                        spinning = refresh == BoardViewModel.RefreshUi.Refreshing,
                        timeLabel = lastUpdated,
                    ) { viewModel.refresh() }
                    ControlIcon("settings", filled = true) { onSettings() }
                }
            }

            // Sub-header
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaterialSymbol("check_circle", tokens.neutrals.textSecondary.toColor(), size = 20.sp, filled = true)
                Text(
                    "Sift Tasks",
                    fontFamily = com.ironclinicgym.sift.ui.theme.Bricolage,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.01).sp,
                    color = tokens.neutrals.textPrimary.toColor(),
                )
            }

            AnimatedVisibility(visible = showReminder) {
                NotionReminderBanner(
                    onDismiss = { viewModel.dismissNotionReminder() },
                )
            }

            projection?.pinnedItems?.let { pinned ->
                if (pinned.isNotEmpty()) {
                    PinnedSection(
                        items = pinned,
                        onTap = { item -> selectedTask = item to "Pinned" },
                    )
                }
            }

            val board = projection
            when {
                board == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                board.priorities.isEmpty() -> EmptyBoard()
                else -> {
                    val twoCol = board.twoColumn
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(if (twoCol) 2 else 1),
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 80.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(board.priorities, key = { it.view.id }) { priority: ProjectedPriority ->
                            PriorityCard(
                                priority = priority,
                                expanded = !twoCol,
                                maxRows = if (twoCol) ROWS_TWO_COLUMN else ROWS_ONE_COLUMN,
                                icon = priorityIconFor(priority.view.colorKey),
                                onOpenPriority = { onOpenPriority(priority.view.id) },
                                onOpenTask = { selectedTask = it to priority.view.displayName },
                            )
                        }
                    }
                }
            }
        }

        // Transient messages at the bottom.
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp),
        ) { Snackbar(it) }

        val boardNotification by viewModel.notification.collectAsStateWithLifecycle()
        val activeNotification = boardNotification

        if (undoToken != null) {
            TopNotificationBar(
                variant = NotificationVariant.Reversible(
                    message = undoToken!!.label,
                    icon = "swap_horiz",
                    onUndo = { viewModel.undo() },
                ),
                onDismiss = { viewModel.dismissUndo() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = insets.calculateTopPadding()),
            )
        } else if (activeNotification != null) {
            val variant = when (activeNotification) {
                is BoardViewModel.BoardNotification.TaskAdded -> {
                    val n = activeNotification as BoardViewModel.BoardNotification.TaskAdded
                    val pColors = priorityColorsOf(n.colorKey)
                    NotificationVariant.ConfirmPlacement(
                        priorityName = n.priorityName,
                        priorityIcon = priorityIconFor(n.colorKey),
                        accentColor = pColors.accent.toColor(),
                        onChange = { viewModel.dismissNotification() },
                    )
                }
                is BoardViewModel.BoardNotification.RefreshSuccess -> NotificationVariant.RefreshSuccess
                is BoardViewModel.BoardNotification.RefreshError -> {
                    val n = activeNotification as BoardViewModel.BoardNotification.RefreshError
                    NotificationVariant.RefreshError(
                        reason = n.reason,
                        accentColor = tokens.overdueText.toColor(),
                        onRetry = { viewModel.dismissNotification(); viewModel.refresh() },
                    )
                }
            }
            TopNotificationBar(
                variant = variant,
                onDismiss = { viewModel.dismissNotification() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = insets.calculateTopPadding()),
            )
        }
      }
    }

    selectedTask?.let { (item, priorityName) ->
        TaskDetailSheet(
            item = item,
            priorityName = priorityName,
            settings = settings,
            onLoadBody = { viewModel.loadPageBody(item.task.pageId) },
            onEdit = { onEditTask(item); selectedTask = null },
            onComplete = { viewModel.completeTask(item.task.pageId); selectedTask = null },
            onPin = { viewModel.pinTask(item.task.pageId); selectedTask = null },
            onRemove = { viewModel.removeTask(item.task.pageId); selectedTask = null },
            onChangeDate = {
                val task = selectedTask?.first?.task ?: return@TaskDetailSheet
                selectedTask = null
                viewModel.openRedirectPromptForTask(task)
            },
            onChangePriority = {
                taskForPriorityPicker = selectedTask?.first
                selectedTask = null
                showPriorityPicker = true
            },
            onDismiss = { selectedTask = null },
        )
    }

    if (showPriorityPicker) {
        val pickerItem = taskForPriorityPicker
        if (pickerItem == null) {
            showPriorityPicker = false
        } else {
            PriorityPickerSheet(
                priorities = settings?.priorities ?: emptyList(),
                currentPriorityOptionId = pickerItem.task.priorityOptionId,
                onSelect = { pv ->
                    viewModel.moveTask(pickerItem.task.pageId, pv.optionName ?: pv.displayName, pv.optionId)
                },
                onDismiss = { showPriorityPicker = false; taskForPriorityPicker = null },
            )
        }
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

@Composable
private fun MinimizedCaptureBar(
    modifier: Modifier = Modifier,
    onTapTask: () -> Unit,
    onTapDump: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.neutrals.surfaceRaised.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(20.dp))
            .clickable(onClick = onTapTask)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "What needs doing?",
            color = tokens.neutrals.textTertiary.toColor(),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        SegmentedToggle(isTask = true, onTask = onTapTask, onDump = onTapDump)
    }
}

@Composable
private fun SegmentedToggle(
    isTask: Boolean,
    onTask: () -> Unit,
    onDump: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        val taskBg = if (isTask) tokens.neutrals.textPrimary.toColor() else androidx.compose.ui.graphics.Color.Transparent
        val taskFg = if (isTask) tokens.neutrals.bg.toColor() else tokens.neutrals.textTertiary.toColor()
        val dumpBg = if (!isTask) tokens.neutrals.textPrimary.toColor() else androidx.compose.ui.graphics.Color.Transparent
        val dumpFg = if (!isTask) tokens.neutrals.bg.toColor() else tokens.neutrals.textTertiary.toColor()
        Row(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(taskBg)
                .clickable(onClick = onTask)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MaterialSymbol("check_circle", taskFg, size = 15.sp, filled = isTask)
            Text("Task", color = taskFg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(dumpBg)
                .clickable(onClick = onDump)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MaterialSymbol("lightbulb", dumpFg, size = 15.sp, filled = !isTask)
            Text("Dump", color = dumpFg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ControlIcon(name: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(name, color = SiftTheme.tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = filled)
    }
}

@Composable
private fun RefreshIcon(spinning: Boolean, timeLabel: String?, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val spinModifier = if (spinning) {
        val transition = rememberInfiniteTransition(label = "refresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
            ),
            label = "refreshSpin",
        )
        Modifier.graphicsLayer { rotationZ = angle }
    } else {
        Modifier
    }
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MaterialSymbol(
            "refresh",
            color = tokens.neutrals.textSecondary.toColor(),
            size = 22.sp,
            modifier = spinModifier,
            filled = false,
        )
        if (!spinning && timeLabel != null) {
            Text(
                timeLabel,
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 12.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}


@Composable
private fun EmptyBoard() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No priorities to show. Check your settings or refresh.",
            color = SiftTheme.tokens.neutrals.textSecondary.toColor(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotionReminderBanner(onDismiss: () -> Unit) {
    val tokens = SiftTheme.tokens
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surfaceRaised.toColor())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MaterialSymbol("notifications", tokens.neutrals.textSecondary.toColor(), size = 20.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Notion may send its own email reminders for tasks with due dates.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.neutrals.textPrimary.toColor(),
            )
            Text(
                "Learn how to turn them off",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.priorities.first().accent.toColor(),
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUrls.NOTION_NOTIFICATIONS)))
                },
            )
        }
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            MaterialSymbol("close", tokens.neutrals.textTertiary.toColor(), size = 16.sp)
        }
    }
}

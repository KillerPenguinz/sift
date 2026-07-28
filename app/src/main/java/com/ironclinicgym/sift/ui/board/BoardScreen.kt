package com.ironclinicgym.sift.ui.board

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.board.resolvedSingleColumnLimit
import com.ironclinicgym.sift.core.domain.SupportUrls
import com.ironclinicgym.sift.core.theme.PRIORITY_META
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
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
    onOpenMenu: () -> Unit,
    onEditTask: (com.ironclinicgym.sift.core.board.ProjectedItem) -> Unit = {},
    /**
     * When set, reopen the task detail sheet for this item. Used to return to the detail view
     * after the user backs out of the edit sheet (which was launched from that detail view).
     */
    reopenDetailFor: com.ironclinicgym.sift.core.board.ProjectedItem? = null,
    onDetailReopened: () -> Unit = {},
) {
    val projection by viewModel.projection.collectAsStateWithLifecycle()
    val label by viewModel.boardLabel.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val refresh by viewModel.refresh.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.lastUpdatedLabel.collectAsStateWithLifecycle()
    val showReminder by viewModel.showNotionReminder.collectAsStateWithLifecycle()
    val redirectPrompt by viewModel.redirectPrompt.collectAsStateWithLifecycle()
    val protectedFriction by viewModel.protectedFriction.collectAsStateWithLifecycle()
    val activeInflationAlert by viewModel.activeInflationAlert.collectAsStateWithLifecycle()
    val tokens = SiftTheme.tokens
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var selectedTask by remember { mutableStateOf<Pair<com.ironclinicgym.sift.core.board.ProjectedItem, String>?>(null) }
    // Title of the most recently removed task, shown in the undo bar in place of the generic
    // core-level label. Not cleared explicitly; it is only read when the active undo token's
    // label is the generic remove label, so a later unrelated undo (move, complete) never
    // picks up a stale title, and a later remove always overwrites it with the new title first.
    var pendingRemoveTitle by remember { mutableStateOf<String?>(null) }

    // Reopen the detail sheet when the edit sheet is backed out of (Section 6: Back returns to the
    // task view, not all the way out). The priority label is recomputed from current settings so an
    // edit that changed the priority shows the right header.
    LaunchedEffect(reopenDetailFor) {
        reopenDetailFor?.let { item ->
            // Prefer the freshest projection copy (keyed by page id) so an edit or a pin change made
            // before backing out shows the current state, not the snapshot from when edit opened.
            val fresh = projection?.let { p ->
                (p.priorities.flatMap { it.items } + p.pinnedItems)
                    .firstOrNull { it.task.pageId == item.task.pageId }
            } ?: item
            val pname = settings?.priorities
                ?.firstOrNull { it.optionId == fresh.task.priorityOptionId }?.displayName.orEmpty()
            selectedTask = fresh to pname
            onDetailReopened()
        }
    }

    // Refresh when the board opens, so data is current without waiting on the background cycle.
    LaunchedEffect(Unit) { viewModel.refresh() }
    // One-shot confirmation when arriving from setup.
    LaunchedEffect(fromSetup) {
        if (fromSetup) viewModel.notifySetupComplete()
    }

    // Post a persistent nudge whenever signal inflation is detected.
    // Key on kind only (not the full alert) so count changes within the same kind don't re-fire.
    LaunchedEffect(activeInflationAlert?.kind) {
        activeInflationAlert?.let { alert ->
            val asapPriorityId = settings?.priorities?.firstOrNull { it.colorKey == "ASAP" }?.id
            when (alert.kind) {
                com.ironclinicgym.sift.core.board.InflationKind.ASAP_OVERLOAD -> viewModel.postNotification(
                    NotificationVariant.InflationNudge(
                        message = "You have ${alert.count} things in ASAP. Want to thin it out?",
                        actionLabel = "Go to ASAP",
                        onAction = { asapPriorityId?.let { onOpenPriority(it) } },
                    )
                )
                com.ironclinicgym.sift.core.board.InflationKind.PROTECTED_SATURATION -> viewModel.postNotification(
                    NotificationVariant.InflationNudge(
                        message = "That is a lot of Protected tasks. Want to review them?",
                        actionLabel = "Review",
                        onAction = { viewModel.openProtectedReview() },
                    )
                )
            }
        }
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
                    val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
                    MenuIconWithBadge(showBadge = unreadNotifications > 0, onClick = onOpenMenu)
                }
            }

            // Sub-header slot: the "Sift Tasks" row when idle, or the current notification when
            // one is queued. The undo bar (if any) takes priority over the queue.
            SubheaderSlot(viewModel = viewModel, bottomPadding = 14.dp, removedTaskTitle = pendingRemoveTitle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialSymbol("check_circle", tokens.neutrals.textSecondary.toColor(), size = 20.sp, filled = true)
                    Text(
                        "Sift Tasks",
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.01).sp,
                        color = tokens.neutrals.textPrimary.toColor(),
                    )
                }
            }

            AnimatedVisibility(visible = showReminder) {
                NotionReminderBanner(
                    onDismiss = { viewModel.dismissNotionReminder() },
                )
            }

            val board = projection
            when {
                board == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                board.priorities.isEmpty() && board.pinnedItems.isEmpty() -> EmptyBoard()
                else -> {
                    val twoCol = board.twoColumn
                    val singleColumnLimit = settings?.resolvedSingleColumnLimit() ?: ROWS_ONE_COLUMN
                    val maxRows = if (twoCol) ROWS_TWO_COLUMN else singleColumnLimit
                    val pinned = board.pinnedItems
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(if (twoCol) 2 else 1),
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 80.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pinned.isNotEmpty()) {
                            item(key = "pinned") {
                                PinnedBucketCard(
                                    items = pinned,
                                    expanded = !twoCol,
                                    maxRows = maxRows,
                                    onOpenTask = { selectedTask = it to "Pinned" },
                                )
                            }
                        }
                        items(board.priorities, key = { it.view.id }) { priority: ProjectedPriority ->
                            PriorityCard(
                                priority = priority,
                                expanded = !twoCol,
                                maxRows = maxRows,
                                icon = priorityIconFor(priority.view.colorKey),
                                onOpenPriority = { onOpenPriority(priority.view.id) },
                                onOpenTask = { selectedTask = it to priority.view.displayName },
                            )
                        }
                    }
                }
            }
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
            // Pin cycles in place; the sheet stays open so the user can keep reviewing the task.
            onPin = { viewModel.pinTask(item.task.pageId) },
            onRemove = {
                pendingRemoveTitle = item.task.title
                viewModel.removeTask(item.task.pageId)
                selectedTask = null
            },
            onChangeDate = {
                val task = selectedTask?.first?.task ?: return@TaskDetailSheet
                selectedTask = null
                viewModel.openRedirectPromptForTask(task)
            },
            onChangePriority = { pv ->
                viewModel.moveTask(item.task.pageId, pv.optionName ?: pv.displayName, pv.optionId)
            },
            onSetBlocked = { blocked -> viewModel.setBlocked(item.task.pageId, blocked) },
            onDismiss = { selectedTask = null },
            frictionActive = protectedFriction != null,
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

    val currentSafetyCatch by viewModel.currentSafetyCatch.collectAsStateWithLifecycle()
    currentSafetyCatch?.let { (task, _) ->
        SafetyCatchDialog(
            task = task,
            onFindBetterSpot = {
                viewModel.openRedirectPromptForTask(task)
                viewModel.dismissSafetyCatch(actedOn = true)
            },
            onDismiss = { viewModel.dismissSafetyCatch() },
        )
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

/**
 * The refresh control: a spinning icon while syncing, plus a status label. During sync the
 * label area shows three bouncing dots; when done, it crossfades to the "updated Xm ago" text.
 * No banner is posted for a successful sync, this indicator is the only signal.
 */
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
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MaterialSymbol(
            "refresh",
            color = tokens.neutrals.textSecondary.toColor(),
            size = 22.sp,
            modifier = spinModifier,
            filled = false,
        )
        Crossfade(targetState = spinning, animationSpec = tween(200), label = "syncStatus") { isSpinning ->
            if (isSpinning) {
                BouncingDots(color = tokens.neutrals.textTertiary.toColor())
            } else if (timeLabel != null) {
                Text(
                    timeLabel,
                    color = tokens.neutrals.textTertiary.toColor(),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

/** Three dots bouncing in sequence, shown in place of the timestamp while a sync is in flight. */
@Composable
private fun BouncingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 133, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(4.dp)
                    .graphicsLayer { translationY = offset }
                    .clip(CircleShape)
                    .background(color),
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

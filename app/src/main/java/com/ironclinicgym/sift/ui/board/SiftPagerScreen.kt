package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.launch

@Composable
fun SiftPagerScreen(
    viewModel: BoardViewModel,
    fromSetup: Boolean,
    onOpenPriority: (String) -> Unit,
    onOpenMenu: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showAddTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ProjectedItem?>(null) }
    var showMinimizedBar by remember { mutableStateOf(false) }
    var selectedBrainDumpItem by remember { mutableStateOf<ProjectedItem?>(null) }

    Box(Modifier.fillMaxSize().background(tokens.neutrals.bg.toColor())) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp + navBarInset),
        ) { page ->
            when (page) {
                0 -> BoardScreen(
                    viewModel = viewModel,
                    fromSetup = fromSetup,
                    onOpenPriority = onOpenPriority,
                    onOpenMenu = onOpenMenu,
                    onEditTask = { item -> editingTask = item },
                )
                1 -> BrainDumpScreen(
                    viewModel = viewModel,
                    onTap = { item -> selectedBrainDumpItem = item },
                    onOpenMenu = onOpenMenu,
                )
            }
        }

        SiftTabBar(
            selectedIndex = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            navBarInset = navBarInset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // FAB
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 60.dp + navBarInset)
                .shadow(8.dp, CircleShape)
                .size(56.dp)
                .clip(CircleShape)
                .background(tokens.neutrals.textPrimary.toColor())
                .clickable { showAddTask = true },
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol("add", tokens.neutrals.bg.toColor(), size = 28.sp)
        }

        // Post-save capture bar — shown after a successful save, invites the next item
        if (showMinimizedBar && !showAddTask) {
            PostSaveCaptureBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp + navBarInset, start = 16.dp, end = 16.dp),
                onTapTask = {
                    showMinimizedBar = false
                    showAddTask = true
                },
                onTapDump = {
                    viewModel.pendingDraft = BoardViewModel.DraftState(
                        title = "",
                        notes = "",
                        bucketId = null,
                        isBrainDump = true,
                        dateIso = null,
                    )
                    showMinimizedBar = false
                    showAddTask = true
                },
                onDismissed = { showMinimizedBar = false },
            )
        }
    }

    val activeSettings = settings
    if ((showAddTask || editingTask != null) && activeSettings != null) {
        AddTaskSheetV2(
            viewModel = viewModel,
            settings = activeSettings,
            editing = editingTask,
            onSaved = {
                showAddTask = false
                editingTask = null
                showMinimizedBar = true
            },
            onDismiss = { showAddTask = false; editingTask = null },
        )
    }

    val showProtectedReview by viewModel.showProtectedReview.collectAsStateWithLifecycle()
    val protectedTasks by viewModel.protectedTasks.collectAsStateWithLifecycle()
    if (showProtectedReview) {
        ProtectedReviewScreen(
            tasks = protectedTasks,
            onUnprotect = { pageId -> viewModel.unprotectTask(pageId) },
            onBack = { viewModel.closeProtectedReview() },
            modifier = Modifier.fillMaxSize(),
        )
    }

    selectedBrainDumpItem?.let { item ->
        BrainDumpDetailSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = { selectedBrainDumpItem = null },
        )
    }
}

@Composable
private fun SiftTabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    navBarInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = SiftTheme.tokens
    Column(
        modifier
            .fillMaxWidth()
            .background(tokens.neutrals.surface.toColor()),
    ) {
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(tokens.neutrals.border.toColor()),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(icon = "check_circle", label = "Board", selected = selectedIndex == 0, onClick = { onSelect(0) })
            TabItem(icon = "lightbulb", label = "Brain dump", selected = selectedIndex == 1, onClick = { onSelect(1) })
        }
        Spacer(Modifier.height(navBarInset))
    }
}

@Composable
private fun TabItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val color = if (selected) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.textTertiary.toColor()
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MaterialSymbol(icon, color, size = 23.sp, filled = selected)
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp,
        )
    }
}

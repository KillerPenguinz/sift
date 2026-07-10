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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.domain.ActionHistoryEntry
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * Action history: the chronological (newest first) log of user actions, opened from the menu
 * hub. The write layer keeps a single active undo token (see [BoardViewModel.undo]), so only
 * the single most recent entry with [ActionHistoryEntry.effectiveCanUndo] true can actually be
 * reverted from here; tapping its Undo button calls the same [BoardViewModel.undo] the inline
 * undo bar uses. The button also requires a live [BoardViewModel.undoToken]: the token is in
 * memory only, so after a dismissed undo bar or process death a row can still read as undoable
 * in Room while nothing can actually revert it. Older reversible entries are shown for
 * reference but without an Undo button. Deeper multi step undo (reverting an arbitrary past
 * entry) is not supported by the write layer and is out of scope here.
 */
@Composable
fun ActionHistoryScreen(
    viewModel: BoardViewModel,
    onBack: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val history by viewModel.actionHistory.collectAsStateWithLifecycle()
    val undoToken by viewModel.undoToken.collectAsStateWithLifecycle()
    val mostRecentUndoableId =
        if (undoToken != null) history.firstOrNull { it.effectiveCanUndo }?.id else null

    Column(
        Modifier
            .fillMaxSize()
            .background(tokens.neutrals.bg.toColor())
            .padding(top = topInset)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                MaterialSymbol("arrow_back", tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = false)
            }
            Spacer(Modifier.weight(1f))
            SiftWordmark()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        Text(
            "Action History",
            fontFamily = Bricolage,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = tokens.neutrals.textPrimary.toColor(),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 24.dp),
        )

        if (history.isEmpty()) {
            Text(
                "No actions yet.",
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { entry ->
                    ActionHistoryRow(
                        entry = entry,
                        showUndo = entry.id == mostRecentUndoableId,
                        onUndo = viewModel::undo,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionHistoryRow(
    entry: ActionHistoryEntry,
    showUndo: Boolean,
    onUndo: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.description,
                color = tokens.neutrals.textPrimary.toColor(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                entry.taskTitle,
                color = tokens.neutrals.textSecondary.toColor(),
                fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            relativeTimeLabel(entry.timestamp),
            color = tokens.neutrals.textTertiary.toColor(),
            fontSize = 12.5.sp,
        )
        if (showUndo) {
            Text(
                "Undo",
                color = tokens.neutrals.textPrimary.toColor(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable(onClick = onUndo),
            )
        }
    }
}

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun BrainDumpScreen(
    viewModel: BoardViewModel,
    onTap: (ProjectedItem) -> Unit,
    onSettings: () -> Unit = {},
) {
    val tokens = SiftTheme.tokens
    val items by viewModel.brainDumpItems.collectAsStateWithLifecycle()
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

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
                Box(Modifier.size(40.dp).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                    MaterialSymbol("settings", tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = true)
                }
            }
        }

        // Sub-header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
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
                items(items, key = { it.task.pageId }) { item ->
                    BrainDumpCard(item = item, onClick = { onTap(item) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun BrainDumpCard(item: ProjectedItem, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.neutrals.borderStrong.toColor()),
        )
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

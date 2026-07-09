package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.board.PriorityColors
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.board.BucketColors
import com.ironclinicgym.sift.core.board.priorityColors
import com.ironclinicgym.sift.core.board.overdueColors
import com.ironclinicgym.sift.core.board.bucketColors
import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.core.theme.BucketKey
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/** Resolve a priority's colors under the active theme, falling back safely for any stray key. */
@Composable
fun priorityColorsOf(colorKey: String): PriorityColors {
    val key = runCatching { PriorityKey.valueOf(colorKey) }.getOrDefault(PriorityKey.ASAP)
    return priorityColors(SiftTheme.tokens.theme, key)
}

@Composable
fun bucketColorsOf(colorKey: String?): BucketColors {
    val key = colorKey?.let { runCatching { BucketKey.valueOf(it) }.getOrNull() }
    return bucketColors(SiftTheme.tokens.theme, key)
}

/** The count pill on a priority header. */
@Composable
fun CountPill(count: Int, colors: PriorityColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.pillBg.toColor())
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("$count", color = colors.pillText.toColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bucket identity tile: color background plus icon, always together, never color alone. */
@Composable
fun BucketTile(iconName: String, colors: BucketColors, tile: Int = 28) {
    Box(
        modifier = Modifier
            .size(tile.dp)
            .clip(RoundedCornerShape((tile / 3).dp))
            .background(colors.bg.toColor()),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(iconName, color = colors.onColor.toColor(), size = (tile * 0.6f).sp)
    }
}

/** Small uppercase overdue tag. */
@Composable
private fun OverdueTag() {
    val (bg, text) = overdueColors(SiftTheme.tokens.theme)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg.toColor())
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("OVERDUE", color = text.toColor(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A compact task row: bucket tile, title (ellipsized), and an overdue tag or time. */
@Composable
fun TaskRow(item: ProjectedItem, expanded: Boolean, onClick: () -> Unit) {
    val sColors = bucketColorsOf(item.bucket.colorKey)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BucketTile(item.bucket.icon, sColors, tile = if (expanded) 34 else 26)
        Column(Modifier.weight(1f)) {
            Text(
                item.task.title,
                color = SiftTheme.tokens.neutrals.textPrimary.toColor(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                val bucketLabel = item.bucket.optionId?.let { item.bucket.displayName }
                val meta = listOfNotNull(bucketLabel, item.timeLabel.takeIf { it.isNotEmpty() }).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, color = SiftTheme.tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (item.labels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.labels.forEach { label ->
                            Text(
                                label,
                                color = SiftTheme.tokens.neutrals.textTertiary.toColor(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
        when {
            item.isOverdue -> OverdueTag()
            item.timeLabel.isNotEmpty() && !expanded ->
                Text(item.timeLabel, color = SiftTheme.tokens.neutrals.textTertiary.toColor(), fontSize = 12.sp)
        }
    }
}

/**
 * A priority box: header wash with the filled icon, lowercase name, and count pill, then up to
 * [maxRows] task rows and a "+N more" line when there are extras. Degrades by dropping rows,
 * never shrinking text. Tapping opens the focused view.
 */
@Composable
fun PriorityCard(
    priority: ProjectedPriority,
    expanded: Boolean,
    maxRows: Int,
    icon: String,
    onOpenPriority: () -> Unit,
    onOpenTask: (ProjectedItem) -> Unit,
) {
    val colors = priorityColorsOf(priority.view.colorKey)
    val tokens = SiftTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(17.dp))
            .background(tokens.neutrals.surface.toColor()),
    ) {
        // The header (and empty / overflow areas) open the whole priority; task rows open the task.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPriority)
                .background(colors.headerBg.toColor())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialSymbol(icon, color = colors.accentText.toColor(), size = 18.sp)
            Text(
                priority.view.displayName.lowercase(),
                color = colors.accentText.toColor(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CountPill(priority.totalCount, colors)
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (priority.items.isEmpty()) {
                Text(
                    "Nothing here",
                    color = tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPriority).padding(vertical = 4.dp),
                )
            }
            priority.items.take(maxRows).forEach { item -> TaskRow(item, expanded) { onOpenTask(item) } }
            val hidden = priority.totalCount - minOf(priority.totalCount, maxRows)
            if (hidden > 0) {
                Text(
                    "plus $hidden more",
                    color = tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPriority).padding(top = 4.dp),
                )
            }
        }
    }
}

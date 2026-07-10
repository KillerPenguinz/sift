package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * The pinned bucket: styled like a priority card (same border, surface, header format, and
 * "plus N more" overflow behavior) so it reads as one of the board's cards rather than a fixed
 * banner. Pinning is about visibility, not urgency, so this card uses the neutral surface
 * treatment (no priority accent color) with a pin icon and count pill in the header. Sits first
 * in the grid's scroll order, scrolling with everything else. Hidden entirely when there are no
 * pinned tasks.
 */
@Composable
fun PinnedBucketCard(
    items: List<ProjectedItem>,
    expanded: Boolean,
    maxRows: Int,
    onOpenTask: (ProjectedItem) -> Unit,
) {
    if (items.isEmpty()) return
    val tokens = SiftTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(17.dp))
            .background(tokens.neutrals.surface.toColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.neutrals.surfaceRaised.toColor())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialSymbol("push_pin", tokens.neutrals.textSecondary.toColor(), size = 18.sp, filled = true)
            Text(
                "Pinned",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PinnedCountPill(items.size)
        }

        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            items.take(maxRows).forEach { item -> TaskRow(item, expanded) { onOpenTask(item) } }
            val hidden = items.size - minOf(items.size, maxRows)
            if (hidden > 0) {
                Text(
                    "plus $hidden more",
                    color = tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PinnedCountPill(count: Int) {
    val tokens = SiftTheme.tokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tokens.neutrals.border.toColor())
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("$count", color = tokens.neutrals.textSecondary.toColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

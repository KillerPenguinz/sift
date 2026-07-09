package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun PinnedSection(
    items: List<ProjectedItem>,
    onTap: (ProjectedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val tokens = SiftTheme.tokens
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MaterialSymbol("push_pin", tokens.neutrals.textTertiary.toColor(), size = 16.sp)
            Text("Pinned", color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.labelMedium)
        }
        items.forEach { item ->
            PinnedRow(item = item, onClick = { onTap(item) })
        }
    }
}

@Composable
private fun PinnedRow(item: ProjectedItem, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    val sColors = bucketColorsOf(item.bucket.colorKey)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.neutrals.surface.toColor())
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BucketTile(item.bucket.icon, sColors, tile = 28)
        Text(
            item.task.title,
            color = tokens.neutrals.textPrimary.toColor(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (item.timeLabel.isNotEmpty()) {
            Text(item.timeLabel, color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

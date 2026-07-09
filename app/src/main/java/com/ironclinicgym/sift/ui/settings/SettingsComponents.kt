package com.ironclinicgym.sift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.board.MaterialSymbol
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/** A small "coming soon" pill for features that are not built yet. */
@Composable
fun ComingSoonPill() {
    val tokens = SiftTheme.tokens
    val accent = tokens.priorities.first { it.name == "one day" }.accent.toColor()
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text("coming soon", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A titled group of settings rows in a single card, iOS-settings style. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tokens = SiftTheme.tokens
    Column {
        Text(
            title,
            color = tokens.neutrals.textTertiary.toColor(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(16.dp))
                .background(tokens.neutrals.surface.toColor()),
            content = content,
        )
    }
}

/** One settings row: optional leading icon, title, optional subtitle, and a trailing slot. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {
        MaterialSymbol("chevron_right", SiftTheme.tokens.neutrals.textTertiary.toColor(), size = 22.sp)
    },
) {
    val tokens = SiftTheme.tokens
    val rowModifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(rowModifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    MaterialSymbol(icon, if (enabled) tokens.neutrals.textSecondary.toColor() else tokens.neutrals.textTertiary.toColor(), size = 22.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) tokens.neutrals.textPrimary.toColor() else tokens.neutrals.textTertiary.toColor(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, color = tokens.neutrals.textTertiary.toColor(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
        if (showDivider) HorizontalDivider(color = tokens.neutrals.border.toColor())
    }
}
